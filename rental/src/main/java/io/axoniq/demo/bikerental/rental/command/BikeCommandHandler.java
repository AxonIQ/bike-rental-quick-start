package io.axoniq.demo.bikerental.rental.command;

import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.command.CommandChannel;
import io.axoniq.axonserver.connector.event.AppendEventsTransaction;
import io.axoniq.axonserver.connector.event.EventChannel;
import io.axoniq.axonserver.grpc.ErrorMessage;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.demo.bikerental.coreapi.rental.ApproveRequestCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RegisterBikeCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RejectRequestCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RequestBikeCommand;
import io.axoniq.demo.bikerental.coreapi.rental.ReturnBikeCommand;
import io.axoniq.demo.bikerental.rental.support.AxonSerializer;
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
 * Command handling for the Bike "aggregate", implemented directly against the Axon Server gRPC API.
 * <p>
 * For every command we:
 * <ol>
 *     <li>load the bike by replaying its event stream ({@link EventChannel#openAggregateStream}),</li>
 *     <li>let the {@link Bike} decision model validate the command and produce new events,</li>
 *     <li>append those events with the next sequence numbers in a single transaction.</li>
 * </ol>
 * A per-bike lock serializes commands for the same bike so sequence numbers never collide within
 * this instance - the role Axon Framework's aggregate locking used to play.
 */
@Component
public class BikeCommandHandler {

    private static final String AGGREGATE_TYPE = "Bike";
    private static final int LOAD_FACTOR = 100;

    private final CommandChannel commandChannel;
    private final EventChannel eventChannel;
    private final AxonSerializer serializer;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    // Command handling loads/appends events over the same connection, so it must not run on the
    // gRPC inbound thread (that would deadlock). We hand the work to a dedicated worker pool.
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    private Registration registration;

    public BikeCommandHandler(CommandChannel commandChannel, EventChannel eventChannel, AxonSerializer serializer) {
        this.commandChannel = commandChannel;
        this.eventChannel = eventChannel;
        this.serializer = serializer;
    }

    @PostConstruct
    public void start() {
        this.registration = commandChannel.registerCommandHandler(
                this::handle,
                LOAD_FACTOR,
                RegisterBikeCommand.class.getName(),
                RequestBikeCommand.class.getName(),
                ApproveRequestCommand.class.getName(),
                RejectRequestCommand.class.getName(),
                ReturnBikeCommand.class.getName());
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
        if (payload instanceof RegisterBikeCommand command) {
            applyToBike(command.bikeId(), bike -> bike.decideOnRegister(command));
            return null;
        } else if (payload instanceof RequestBikeCommand command) {
            String rentalReference = UUID.randomUUID().toString();
            applyToBike(command.bikeId(), bike -> bike.decideOnRequest(command, rentalReference));
            return rentalReference;
        } else if (payload instanceof ApproveRequestCommand command) {
            applyToBike(command.bikeId(), bike -> bike.decideOnApprove(command));
            return null;
        } else if (payload instanceof RejectRequestCommand command) {
            applyToBike(command.bikeId(), bike -> bike.decideOnReject(command));
            return null;
        } else if (payload instanceof ReturnBikeCommand command) {
            applyToBike(command.bikeId(), bike -> bike.decideOnReturn(command));
            return null;
        }
        throw new IllegalArgumentException("Unsupported command: " + payload.getClass());
    }

    private void applyToBike(String bikeId, java.util.function.Function<Bike, List<Object>> decision) {
        synchronized (locks.computeIfAbsent(bikeId, k -> new Object())) {
            Bike bike = load(bikeId);
            List<Object> events = decision.apply(bike);
            append(bikeId, bike.lastSequence(), events);
        }
    }

    private Bike load(String bikeId) {
        Bike bike = new Bike();
        // allowSnapshots = false: this model never produces snapshots, so we replay the full history.
        eventChannel.openAggregateStream(bikeId, false)
                    .asStream()
                    .forEach(event -> {
                        bike.setLastSequence(event.getAggregateSequenceNumber());
                        bike.apply(serializer.deserialize(event.getPayload()));
                    });
        return bike;
    }

    private void append(String bikeId, long lastSequence, List<Object> events) {
        if (events.isEmpty()) {
            return;
        }
        AppendEventsTransaction transaction = eventChannel.startAppendEventsTransaction();
        long sequence = lastSequence;
        for (Object payload : events) {
            sequence++;
            transaction.appendEvent(Event.newBuilder()
                                         .setMessageIdentifier(UUID.randomUUID().toString())
                                         .setAggregateIdentifier(bikeId)
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
            throw new IllegalStateException("Failed to append events for bike " + bikeId, e);
        }
    }

    private CommandResponse error(Command command, Exception e) {
        Throwable cause = e instanceof java.util.concurrent.CompletionException && e.getCause() != null
                ? e.getCause() : e;
        return CommandResponse.newBuilder()
                              .setMessageIdentifier(UUID.randomUUID().toString())
                              .setRequestIdentifier(command.getMessageIdentifier())
                              .setErrorCode("AXONIQ-4002")
                              .setErrorMessage(ErrorMessage.newBuilder()
                                                           .setMessage(String.valueOf(cause.getMessage()))
                                                           .setLocation("BikeCommandHandler")
                                                           .build())
                              .build();
    }
}
