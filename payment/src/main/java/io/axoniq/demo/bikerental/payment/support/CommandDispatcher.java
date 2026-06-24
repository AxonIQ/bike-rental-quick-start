package io.axoniq.demo.bikerental.payment.support;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin replacement for Axon Framework's {@code CommandGateway}, built on the Axon Server Integration
 * HTTP API: a command is sent with {@code POST /v2/commands} and routed by Axon Server to the
 * registered command handler. The blocking HTTP call runs on a worker pool to keep callers async.
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
