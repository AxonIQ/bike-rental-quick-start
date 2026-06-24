package io.axoniq.demo.bikerental.rental.support;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin replacement for Axon Framework's {@code CommandGateway}, built on the Axon Server Integration
 * HTTP API. A command is sent with {@code POST /v2/commands}: its {@code name}/{@code payloadType} is
 * the payload's fully qualified class name (how the handler is registered) and its {@code payload} is
 * the JSON form of the command object. Axon Server routes it to the registered command handler.
 * <p>
 * The HTTP call is blocking, so it is run on a small worker pool to keep callers asynchronous (the
 * controllers return {@link CompletableFuture}).
 */
@Component
public class CommandDispatcher {

    private final AxonServerClient client;
    private final AxonSerializer serializer;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public CommandDispatcher(AxonServerClient client, AxonSerializer serializer) {
        this.client = client;
        this.serializer = serializer;
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }

    public CompletableFuture<Void> send(Object payload) {
        return send(payload, Void.class);
    }

    public <R> CompletableFuture<R> send(Object payload, Class<R> responseType) {
        AxonServerMessages.Command command = new AxonServerMessages.Command(
                payload.getClass().getName(),
                payload.getClass().getName(),
                null,
                null,
                null,
                UUID.randomUUID().toString(),
                null,
                serializer.toJson(payload));
        return CompletableFuture.supplyAsync(() -> {
            AxonServerMessages.CommandResult result = client.sendCommand(command);
            if (responseType == Void.class || result == null || result.payload() == null) {
                return null;
            }
            return serializer.fromJson(result.payload(), responseType);
        }, executor);
    }
}
