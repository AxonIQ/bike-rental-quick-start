package io.axoniq.demo.bikerental.rental.query;

import io.axoniq.demo.bikerental.coreapi.rental.BikeInUseEvent;
import io.axoniq.demo.bikerental.coreapi.rental.BikeRegisteredEvent;
import io.axoniq.demo.bikerental.coreapi.rental.BikeRequestedEvent;
import io.axoniq.demo.bikerental.coreapi.rental.BikeReturnedEvent;
import io.axoniq.demo.bikerental.coreapi.rental.BikeStatus;
import io.axoniq.demo.bikerental.coreapi.rental.BikeStatusNamedQueries;
import io.axoniq.demo.bikerental.coreapi.rental.CountOfBikesByTypeQuery;
import io.axoniq.demo.bikerental.coreapi.rental.RentalStatus;
import io.axoniq.demo.bikerental.coreapi.rental.RequestRejectedEvent;
import io.axoniq.demo.bikerental.rental.support.AxonSerializer;
import io.axoniq.demo.bikerental.rental.support.AxonServerMessages;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * The read model for bikes. It answers point-to-point queries (invoked by the
 * {@code QueryHandlerController} when Axon Server routes a query to us) and is fed events by the
 * {@code EventHandlerController}. Because the read model lives in this JVM, live UI updates are
 * served directly from here via a {@link SubscriptionRegistry} - the HTTP-branch stand-in for the
 * subscription queries the gRPC branch used (the HTTP query API has no subscription support).
 */
@Component
public class BikeStatusProjection {

    private final BikeStatusRepository repository;
    private final AxonSerializer serializer;
    private final SubscriptionRegistry<BikeStatus> subscriptions = new SubscriptionRegistry<>();

    public BikeStatusProjection(BikeStatusRepository repository, AxonSerializer serializer) {
        this.repository = repository;
        this.serializer = serializer;
    }

    // ---------------------------------------------------------------------------------------------
    // Query handling (point-to-point)
    // ---------------------------------------------------------------------------------------------

    public Object handle(AxonServerMessages.Query query) {
        return switch (query.name()) {
            case BikeStatusNamedQueries.FIND_ALL -> repository.findAll();
            case BikeStatusNamedQueries.FIND_AVAILABLE ->
                    repository.findAllByBikeTypeAndStatus(payloadAsString(query), RentalStatus.AVAILABLE);
            case BikeStatusNamedQueries.FIND_ONE -> repository.findById(payloadAsString(query)).orElse(null);
            default -> {
                if (CountOfBikesByTypeQuery.class.getName().equals(query.name())) {
                    CountOfBikesByTypeQuery payload =
                            serializer.fromJson(query.payload(), CountOfBikesByTypeQuery.class);
                    yield repository.countBikeStatusesByBikeType(payload.bikeType());
                }
                throw new IllegalArgumentException("No handler for query " + query.name());
            }
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Live updates (local subscriptions, served to the UI)
    // ---------------------------------------------------------------------------------------------

    public Flux<BikeStatus> subscribeAll() {
        return Flux.fromIterable(repository.findAll()).concatWith(subscriptions.register(null));
    }

    public Flux<BikeStatus> subscribeOne(String bikeId) {
        Flux<BikeStatus> initial = repository.findById(bikeId).map(Flux::just).orElseGet(Flux::empty);
        return initial.concatWith(subscriptions.register(bikeId));
    }

    // ---------------------------------------------------------------------------------------------
    // Event handling (called by the event handler controller, transactionally)
    // ---------------------------------------------------------------------------------------------

    @Transactional
    public void on(Object event) {
        if (event instanceof BikeRegisteredEvent e) {
            BikeStatus status = new BikeStatus(e.bikeId(), e.bikeType(), e.location());
            emit(repository.save(status));
        } else if (event instanceof BikeRequestedEvent e) {
            repository.findById(e.bikeId()).ifPresent(status -> {
                status.requestedBy(e.renter());
                emit(repository.save(status));
            });
        } else if (event instanceof BikeInUseEvent e) {
            repository.findById(e.bikeId()).ifPresent(status -> {
                status.rentedBy(e.renter());
                emit(repository.save(status));
            });
        } else if (event instanceof BikeReturnedEvent e) {
            repository.findById(e.bikeId()).ifPresent(status -> {
                status.returnedAt(e.location());
                emit(repository.save(status));
            });
        } else if (event instanceof RequestRejectedEvent e) {
            repository.findById(e.bikeId()).ifPresent(status -> {
                status.returnedAt(status.getLocation());
                emit(repository.save(status));
            });
        }
    }

    private void emit(BikeStatus status) {
        subscriptions.emit(status.getBikeId(), status);
    }

    private String payloadAsString(AxonServerMessages.Query query) {
        return serializer.fromJson(query.payload(), String.class);
    }
}
