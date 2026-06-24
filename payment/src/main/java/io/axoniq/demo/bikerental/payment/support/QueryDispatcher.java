package io.axoniq.demo.bikerental.payment.support;

import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.connector.query.QueryChannel;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Thin replacement for Axon Framework's {@code QueryGateway} (point-to-point queries only, which is
 * all the payment service dispatches), built on the Axon Server {@link QueryChannel}.
 */
@Component
public class QueryDispatcher {

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
}
