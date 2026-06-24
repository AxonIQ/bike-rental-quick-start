package io.axoniq.demo.bikerental.payment;

import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.command.CommandChannel;
import io.axoniq.axonserver.connector.event.AppendEventsTransaction;
import io.axoniq.axonserver.connector.event.EventChannel;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.demo.bikerental.coreapi.payment.ConfirmPaymentCommand;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentPreparedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PreparePaymentCommand;
import io.axoniq.demo.bikerental.coreapi.payment.RejectPaymentCommand;
import io.axoniq.demo.bikerental.payment.support.AxonSerializer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Command handling for the Payment "aggregate", implemented directly against the Axon Server gRPC
 * API. {@code PreparePaymentCommand} creates a new payment (a fresh aggregate id + first event);
 * confirm/reject load the payment by replaying its events, validate, and append a new event.
 */
@Component
public class PaymentCommandHandler {

    private static final String AGGREGATE_TYPE = "Payment";
    private static final int LOAD_FACTOR = 100;

    private final CommandChannel commandChannel;
    private final EventChannel eventChannel;
    private final AxonSerializer serializer;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    // Command handling loads/appends events over the same connection, so it must not run on the
    // gRPC inbound thread (that would deadlock). We hand the work to a dedicated worker pool.
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    private Registration registration;

    public PaymentCommandHandler(CommandChannel commandChannel, EventChannel eventChannel, AxonSerializer serializer) {
        this.commandChannel = commandChannel;
        this.eventChannel = eventChannel;
        this.serializer = serializer;
    }

    @PostConstruct
    public void start() {
        this.registration = commandChannel.registerCommandHandler(
                this::handle,
                LOAD_FACTOR,
                PreparePaymentCommand.class.getName(),
                ConfirmPaymentCommand.class.getName(),
                RejectPaymentCommand.class.getName());
    }

    @PreDestroy
    public void stop() {
        if (registration != null) {
            registration.cancel();
        }
        executor.shutdownNow();
    }

    private CompletableFuture<CommandResponse> handle(Command command) {
        return CompletableFuture.supplyAsync(() -> compute(command), executor);
    }

    private CommandResponse compute(Command command) {
        try {
            Object payload = serializer.deserialize(command.getPayload());
            Object result = dispatch(payload);
            CommandResponse.Builder response = CommandResponse.newBuilder()
                                                              .setMessageIdentifier(UUID.randomUUID().toString())
                                                              .setRequestIdentifier(command.getMessageIdentifier());
            if (result != null) {
                response.setPayload(serializer.serialize(result));
            }
            return response.build();
        } catch (Exception e) {
            return error(command, e);
        }
    }

    private Object dispatch(Object payload) {
        if (payload instanceof PreparePaymentCommand command) {
            String paymentId = UUID.randomUUID().toString();
            // A new payment: no history to load, just append the first event under a fresh id.
            append(paymentId, -1, List.of(
                    new PaymentPreparedEvent(paymentId, command.amount(), command.paymentReference())));
            return paymentId;
        } else if (payload instanceof ConfirmPaymentCommand command) {
            applyToPayment(command.paymentId(), payment -> payment.decideOnConfirm(command));
            return null;
        } else if (payload instanceof RejectPaymentCommand command) {
            applyToPayment(command.paymentId(), payment -> payment.decideOnReject(command));
            return null;
        }
        throw new IllegalArgumentException("Unsupported command: " + payload.getClass());
    }

    private void applyToPayment(String paymentId, java.util.function.Function<Payment, List<Object>> decision) {
        synchronized (locks.computeIfAbsent(paymentId, k -> new Object())) {
            Payment payment = load(paymentId);
            List<Object> events = decision.apply(payment);
            append(paymentId, payment.lastSequence(), events);
        }
    }

    private Payment load(String paymentId) {
        Payment payment = new Payment();
        eventChannel.openAggregateStream(paymentId, false)
                    .asStream()
                    .forEach(event -> {
                        payment.setLastSequence(event.getAggregateSequenceNumber());
                        payment.apply(serializer.deserialize(event.getPayload()));
                    });
        return payment;
    }

    private void append(String paymentId, long lastSequence, List<Object> events) {
        if (events.isEmpty()) {
            return;
        }
        AppendEventsTransaction transaction = eventChannel.startAppendEventsTransaction();
        long sequence = lastSequence;
        for (Object payload : events) {
            sequence++;
            transaction.appendEvent(Event.newBuilder()
                                         .setMessageIdentifier(UUID.randomUUID().toString())
                                         .setAggregateIdentifier(paymentId)
                                         .setAggregateType(AGGREGATE_TYPE)
                                         .setAggregateSequenceNumber(sequence)
                                         .setTimestamp(System.currentTimeMillis())
                                         .setPayload(serializer.serialize(payload))
                                         .build());
        }
        try {
            transaction.commit().get();
        } catch (Exception e) {
            transaction.rollback();
            throw new IllegalStateException("Failed to append events for payment " + paymentId, e);
        }
    }

    private CommandResponse error(Command command, Exception e) {
        return CommandResponse.newBuilder()
                              .setMessageIdentifier(UUID.randomUUID().toString())
                              .setRequestIdentifier(command.getMessageIdentifier())
                              .setErrorCode("AXONIQ-4002")
                              .setErrorMessage(ErrorMessage.newBuilder()
                                                           .setMessage(String.valueOf(e.getMessage()))
                                                           .setLocation("PaymentCommandHandler")
                                                           .build())
                              .build();
    }
}
