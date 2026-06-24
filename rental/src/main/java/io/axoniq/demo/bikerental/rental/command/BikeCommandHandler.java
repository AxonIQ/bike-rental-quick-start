package io.axoniq.demo.bikerental.rental.command;

import com.fasterxml.jackson.databind.JsonNode;
import io.axoniq.demo.bikerental.coreapi.rental.ApproveRequestCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RegisterBikeCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RejectRequestCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RequestBikeCommand;
import io.axoniq.demo.bikerental.coreapi.rental.ReturnBikeCommand;
import io.axoniq.demo.bikerental.rental.support.AxonSerializer;
import io.axoniq.demo.bikerental.rental.support.AxonServerClient;
import io.axoniq.demo.bikerental.rental.support.AxonServerMessages;
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
 * Command handling for the Bike "aggregate", implemented against the Axon Server Integration HTTP
 * API. It is invoked by the {@code CommandHandlerController} when Axon Server routes a command to us.
 * For every command we:
 * <ol>
 *     <li>load the bike by reading its event stream ({@link AxonServerClient#readAggregateEvents}),</li>
 *     <li>let the {@link Bike} decision model validate the command and produce new events,</li>
 *     <li>append those events with the next sequence numbers ({@link AxonServerClient#appendEvents}).</li>
 * </ol>
 * A per-bike lock serializes commands for the same bike so sequence numbers never collide within this
 * instance - the role Axon Framework's aggregate locking used to play. The work runs on a worker pool
 * because it makes blocking HTTP calls to Axon Server.
 */
@Component
public class BikeCommandHandler {

    private static final String AGGREGATE_TYPE = "Bike";

    private final AxonServerClient client;
    private final AxonSerializer serializer;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public BikeCommandHandler(AxonServerClient client, AxonSerializer serializer) {
        this.client = client;
        this.serializer = serializer;
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }

    /** Handles one routed command, returning the (optional) JSON result payload. */
    public CompletableFuture<JsonNode> handle(AxonServerMessages.Command command) {
        return CompletableFuture.supplyAsync(() -> {
            Object payload = serializer.fromJson(command.payload(), commandType(command));
            Object result = dispatch(payload);
            return result == null ? null : serializer.toJson(result);
        }, executor);
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

    private void applyToBike(String bikeId, Function<Bike, List<Object>> decision) {
        synchronized (locks.computeIfAbsent(bikeId, k -> new Object())) {
            Bike bike = load(bikeId);
            List<Object> events = decision.apply(bike);
            append(bikeId, bike.lastSequence(), events);
        }
    }

    private Bike load(String bikeId) {
        Bike bike = new Bike();
        for (AxonServerMessages.Event event : client.readAggregateEvents(bikeId)) {
            bike.setLastSequence(event.sequenceNumber());
            bike.apply(serializer.fromJson(event.payload(), event.type()));
        }
        return bike;
    }

    private void append(String bikeId, long lastSequence, List<Object> events) {
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
                    bikeId, AGGREGATE_TYPE, sequence, null, null, null,
                    serializer.toJson(payload)));
        }
        client.appendEvents(messages);
    }

    private static String commandType(AxonServerMessages.Command command) {
        return command.payloadType() != null ? command.payloadType() : command.name();
    }
}
