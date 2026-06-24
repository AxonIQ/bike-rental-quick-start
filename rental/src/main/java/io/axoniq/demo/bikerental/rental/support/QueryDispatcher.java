package io.axoniq.demo.bikerental.rental.support;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin replacement for Axon Framework's {@code QueryGateway}, built on the Axon Server Integration
 * HTTP API. Point-to-point queries are sent with {@code POST /v2/queries} and routed by Axon Server
 * to the registered query handler.
 * <p>
 * The HTTP query API does <strong>not</strong> support subscription queries. Where the gRPC branch
 * used a subscription query to wait for a value to appear (and for cross-service waits in particular),
 * we emulate it here by polling the point-to-point query and emitting on change - see
 * {@link #subscriptionQuery}. Live UI updates for the local read model are instead served by the
 * projection's own in-memory registry (see {@code BikeStatusProjection}).
 */
@Component
public class QueryDispatcher {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

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

    /**
     * Emulates a subscription query by polling the point-to-point query at a fixed interval and
     * emitting whenever the result changes. {@code null} results (nothing yet) are skipped, so a
     * consumer can simply take the first emitted value once it appears.
     */
    public <R> Flux<R> subscriptionQuery(String queryName, Object payload, Class<R> responseType) {
        return Flux.interval(Duration.ZERO, POLL_INTERVAL)
                   .concatMap(tick -> Mono.fromFuture(query(queryName, payload, responseType)))
                   .distinctUntilChanged();
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
