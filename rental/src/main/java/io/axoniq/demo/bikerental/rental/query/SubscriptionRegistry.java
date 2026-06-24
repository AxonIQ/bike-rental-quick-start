package io.axoniq.demo.bikerental.rental.query;

import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.query.QueryHandler;
import io.axoniq.axonserver.grpc.query.QueryUpdate;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of the active subscription queries against a projection and pushes updates to them.
 * This is the small piece that replaces Axon Framework's {@code QueryUpdateEmitter}: when the
 * projection changes, it asks this registry to emit a {@link QueryUpdate} to every subscription
 * whose query name matches and whose (optional) filter value equals the one supplied.
 */
public class SubscriptionRegistry {

    private record Subscription(String queryName, Object filter, QueryHandler.UpdateHandler handler) {

    }

    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();

    /**
     * Registers a subscription for the given query name. A {@code null} filter matches every emit
     * for that query name (used for "find all"); a non-null filter only matches emits carrying an
     * equal filter value (used for "find one" by id).
     */
    public Registration register(String queryName, Object filter, QueryHandler.UpdateHandler handler) {
        Subscription subscription = new Subscription(queryName, filter, handler);
        subscriptions.add(subscription);
        return () -> {
            subscriptions.remove(subscription);
            return CompletableFuture.completedFuture(null);
        };
    }

    public void emit(String queryName, Object filterValue, QueryUpdate update) {
        for (Subscription subscription : subscriptions) {
            if (subscription.queryName().equals(queryName)
                    && (subscription.filter() == null || Objects.equals(subscription.filter(), filterValue))) {
                subscription.handler().sendUpdate(update);
            }
        }
    }
}
