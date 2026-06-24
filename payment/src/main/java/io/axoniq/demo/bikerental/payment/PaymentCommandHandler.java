package io.axoniq.demo.bikerental.payment;

import com.fasterxml.jackson.databind.JsonNode;
import io.axoniq.demo.bikerental.coreapi.payment.ConfirmPaymentCommand;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentPreparedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PreparePaymentCommand;
import io.axoniq.demo.bikerental.coreapi.payment.RejectPaymentCommand;
import io.axoniq.demo.bikerental.payment.support.AxonSerializer;
import io.axoniq.demo.bikerental.payment.support.AxonServerClient;
import io.axoniq.demo.bikerental.payment.support.AxonServerMessages;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Command handling for the Payment "aggregate", implemented against the Axon Server Integration HTTP
 * API and invoked by the {@code CommandHandlerController}. {@code PreparePaymentCommand} creates a
 * new payment (a fresh aggregate id + first event); confirm/reject load the payment by replaying its
 * events ({@link AxonServerClient#readAggregateEvents}), validate, and append a new event.
 */
@Component
public class PaymentCommandHandler {

    private static final String AGGREGATE_TYPE = "Payment";

    private final AxonServerClient client;
    private final AxonSerializer serializer;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public PaymentCommandHandler(AxonServerClient client, AxonSerializer serializer) {
        this.client = client;
        this.serializer = serializer;
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }

    public CompletableFuture<JsonNode> handle(AxonServerMessages.Command command) {
        return CompletableFuture.supplyAsync(() -> {
            Object payload = serializer.fromJson(command.payload(), commandType(command));
            Object result = dispatch(payload);
            return result == null ? null : serializer.toJson(result);
        }, executor);
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

    private void applyToPayment(String paymentId, Function<Payment, List<Object>> decision) {
        synchronized (locks.computeIfAbsent(paymentId, k -> new Object())) {
            Payment payment = load(paymentId);
            List<Object> events = decision.apply(payment);
            append(paymentId, payment.lastSequence(), events);
        }
    }

    private Payment load(String paymentId) {
        Payment payment = new Payment();
        for (AxonServerMessages.Event event : client.readAggregateEvents(paymentId)) {
            payment.setLastSequence(event.sequenceNumber());
            payment.apply(serializer.fromJson(event.payload(), event.type()));
        }
        return payment;
    }

    private void append(String paymentId, long lastSequence, List<Object> events) {
        if (events.isEmpty()) {
            return;
        }
        List<AxonServerMessages.Event> messages = new ArrayList<>();
        long sequence = lastSequence;
        for (Object payload : events) {
            sequence++;
            String type = payload.getClass().getName();
            messages.add(new AxonServerMessages.Event(
                    type, type, null,
                    UUID.randomUUID().toString(),
                    paymentId, AGGREGATE_TYPE, sequence, null, null, null,
                    serializer.toJson(payload)));
        }
        client.appendEvents(messages);
    }

    private static String commandType(AxonServerMessages.Command command) {
        return command.payloadType() != null ? command.payloadType() : command.name();
    }
}
