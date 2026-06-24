package io.axoniq.demo.bikerental.payment.support;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin replacement for Axon Framework's {@code QueryGateway} (point-to-point queries only, which is
 * all the payment service dispatches), built on the Axon Server Integration HTTP API: a query is sent
 * with {@code POST /v2/queries} and routed by Axon Server to the registered query handler.
 */
@Component
public class QueryDispatcher {

    private final AxonServerClient client;
    private final AxonSerializer serializer;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    public QueryDispatcher(AxonServerClient client, AxonSerializer serializer) {
        this.client = client;
        this.serializer = serializer;
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }

    public <R> CompletableFuture<R> query(String queryName, Object payload, Class<R> responseType) {
        return CompletableFuture.supplyAsync(() -> {
            AxonServerMessages.QueryResult result =
                    client.sendQuery(buildQuery(queryName, payload, responseType, "SINGLE"));
            return result == null ? null : serializer.fromJson(result.payload(), responseType);
        }, executor);
    }

    public <R> CompletableFuture<List<R>> queryMany(String queryName, Object payload, Class<R> elementType) {
        return CompletableFuture.supplyAsync(() -> {
            AxonServerMessages.QueryResult result =
                    client.sendQuery(buildQuery(queryName, payload, elementType, "MULTIPLE"));
            return result == null ? List.<R>of() : serializer.listFromJson(result.payload(), elementType);
        }, executor);
    }

    private AxonServerMessages.Query buildQuery(String queryName, Object payload,
                                                Class<?> responseType, String cardinality) {
        return new AxonServerMessages.Query(
                queryName,
                payload == null ? null : payload.getClass().getName(),
                null,
                responseType.getName(),
                cardinality,
                null,
                UUID.randomUUID().toString(),
                null,
                payload == null ? null : serializer.toJson(payload));
    }
}
