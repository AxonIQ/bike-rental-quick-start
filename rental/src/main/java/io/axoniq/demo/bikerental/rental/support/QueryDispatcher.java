package io.axoniq.demo.bikerental.rental.support;

import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.query.QueryChannel;
import io.axoniq.axonserver.connector.query.SubscriptionQueryResult;
import io.axoniq.axonserver.grpc.SerializedObject;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Thin replacement for Axon Framework's {@code QueryGateway}, built on the Axon Server
 * {@link QueryChannel}. Point-to-point queries use {@link QueryChannel#query}, while
 * subscription queries use {@link QueryChannel#subscriptionQuery} and are exposed as a Reactor
 * {@link Flux} of the initial result followed by live updates.
 * <p>
 * The convention for collection results is that the handler answers a single {@link QueryResponse}
 * whose payload is a JSON array; {@link #queryMany} / {@link #subscriptionQueryMany} decode it.
 */
@Component
public class QueryDispatcher {

    private static final int UPDATE_BUFFER_SIZE = 1024;
    private static final int UPDATE_FETCH_SIZE = 8;

    private final QueryChannel queryChannel;
    private final AxonSerializer serializer;

    public QueryDispatcher(QueryChannel queryChannel, AxonSerializer serializer) {
        this.queryChannel = queryChannel;
        this.serializer = serializer;
    }

    public <R> CompletableFuture<R> query(String queryName, Object payload, Class<R> responseType) {
        return firstResponse(queryChannel.query(buildRequest(queryName, payload)))
                .thenApply(response -> response.hasPayload()
                        ? serializer.deserialize(response.getPayload(), responseType)
                        : null);
    }

    public <R> CompletableFuture<List<R>> queryMany(String queryName, Object payload, Class<R> elementType) {
        return firstResponse(queryChannel.query(buildRequest(queryName, payload)))
                .thenApply(response -> response.hasPayload()
                        ? serializer.deserializeList(response.getPayload(), elementType)
                        : List.<R>of());
    }

    public <R> Flux<R> subscriptionQuery(String queryName, Object payload, Class<R> type) {
        SubscriptionQueryResult result = openSubscription(queryName, payload, type);
        Mono<R> initial = Mono.fromFuture(result::initialResult)
                              .mapNotNull(response -> response.hasPayload()
                                      ? serializer.deserialize(response.getPayload(), type)
                                      : null);
        Flux<R> updates = updateFlux(result.updates())
                .map(update -> serializer.deserialize(update.getPayload(), type));
        return initial.concatWith(updates);
    }

    public <R> Flux<R> subscriptionQueryMany(String queryName, Object payload, Class<R> elementType) {
        SubscriptionQueryResult result = openSubscription(queryName, payload, elementType);
        Flux<R> initial = Mono.fromFuture(result::initialResult)
                              .flatMapMany(response -> response.hasPayload()
                                      ? Flux.fromIterable(serializer.deserializeList(response.getPayload(), elementType))
                                      : Flux.empty());
        Flux<R> updates = updateFlux(result.updates())
                .map(update -> serializer.deserialize(update.getPayload(), elementType));
        return initial.concatWith(updates);
    }

    private SubscriptionQueryResult openSubscription(String queryName, Object payload, Class<?> updateType) {
        SerializedObject updateResponseType = SerializedObject.newBuilder()
                                                              .setType(updateType.getName())
                                                              .build();
        return queryChannel.subscriptionQuery(buildRequest(queryName, payload),
                                              updateResponseType,
                                              UPDATE_BUFFER_SIZE,
                                              UPDATE_FETCH_SIZE);
    }

    private QueryRequest buildRequest(String queryName, Object payload) {
        QueryRequest.Builder builder = QueryRequest.newBuilder().setQuery(queryName);
        if (payload != null) {
            builder.setPayload(serializer.serialize(payload));
        }
        return builder.build();
    }

    private CompletableFuture<QueryResponse> firstResponse(ResultStream<QueryResponse> stream) {
        CompletableFuture<QueryResponse> future = new CompletableFuture<>();
        stream.onAvailable(() -> {
            try {
                QueryResponse response;
                while ((response = stream.nextIfAvailable()) != null) {
                    if (future.isDone()) {
                        continue;
                    }
                    if (!response.getErrorCode().isEmpty()) {
                        future.completeExceptionally(
                                new QueryExecutionException(response.getErrorMessage().getMessage()));
                    } else {
                        future.complete(response);
                    }
                }
                if (stream.isClosed() && !future.isDone()) {
                    future.completeExceptionally(stream.getError()
                                                       .orElseGet(() -> new QueryExecutionException(
                                                               "Query completed without a result")));
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        future.whenComplete((r, e) -> stream.close());
        return future;
    }

    private Flux<QueryUpdate> updateFlux(ResultStream<QueryUpdate> stream) {
        return Flux.create(sink -> {
            stream.onAvailable(() -> {
                try {
                    QueryUpdate update;
                    while ((update = stream.nextIfAvailable()) != null) {
                        sink.next(update);
                    }
                    if (stream.isClosed()) {
                        stream.getError().ifPresentOrElse(sink::error, sink::complete);
                    }
                } catch (Exception e) {
                    sink.error(e);
                }
            });
            sink.onDispose(stream::close);
        });
    }
}
