package io.axoniq.demo.bikerental.payment.query;

import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.query.QueryHandler;
import io.axoniq.axonserver.grpc.query.QueryUpdate;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active subscription queries and pushes updates to them - the replacement for Axon
 * Framework's {@code QueryUpdateEmitter}.
 */
public class SubscriptionRegistry {

    private record Subscription(String queryName, Object filter, QueryHandler.UpdateHandler handler) {

    }

    private final Set<Subscription> subscriptions = ConcurrentHashMap.newKeySet();

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
