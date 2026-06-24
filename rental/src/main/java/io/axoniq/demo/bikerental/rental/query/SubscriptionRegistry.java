package io.axoniq.demo.bikerental.rental.query;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of active in-memory subscriptions to a projection and pushes updates to them. This is
 * the HTTP-branch replacement for Axon Framework's {@code QueryUpdateEmitter} (and for the gRPC
 * branch's subscription-query support, which the HTTP query API does not offer): because the read
 * model lives in this JVM and is fed by an event-handler callback, we can serve live UI updates
 * locally as a Reactor {@link Flux}.
 * <p>
 * A subscription has an optional filter: a {@code null} filter receives every emit (used for
 * "find all"); a non-null filter only receives emits whose key equals it (used for "find one" by id).
 */
public class SubscriptionRegistry<T> {

    private record Subscription<T>(Object filter, Sinks.Many<T> sink) {

    }

    private final Set<Subscription<T>> subscriptions = ConcurrentHashMap.newKeySet();

    public Flux<T> register(Object filter) {
        Sinks.Many<T> sink = Sinks.many().multicast().onBackpressureBuffer();
        Subscription<T> subscription = new Subscription<>(filter, sink);
        subscriptions.add(subscription);
        return sink.asFlux().doFinally(signal -> subscriptions.remove(subscription));
    }

    public void emit(Object key, T value) {
        for (Subscription<T> subscription : subscriptions) {
            if (subscription.filter() == null || Objects.equals(subscription.filter(), key)) {
                subscription.sink().tryEmitNext(value);
            }
        }
    }
}
