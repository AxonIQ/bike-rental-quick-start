package io.axoniq.demo.bikerental.rental.query;

import io.axoniq.demo.bikerental.coreapi.rental.*;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.springframework.stereotype.Component;

//tag::ClassDefinition[]
@Component
public class BikeStatusProjection {

    //tag::Repository[]
    private final BikeStatusRepository bikeStatusRepository; //<.>
    //end::Repository[]
    //tag::UpdateEmitter[]
    private final QueryUpdateEmitter updateEmitter;

    //end::UpdateEmitter[]
    //tag::Constructor[]
    public BikeStatusProjection(BikeStatusRepository bikeStatusRepository, QueryUpdateEmitter updateEmitter) {
        this.bikeStatusRepository = bikeStatusRepository;
        this.updateEmitter = updateEmitter;
    }

    //end::Constructor[]
    //tag::EventHandlers[]
    //tag::BikeRegisteredEventHandler[]
    @EventHandler //<.>
    public void on(BikeRegisteredEvent event) { //<.>
        var bikeStatus = new BikeStatus(event.bikeId(), event.bikeType(), event.location()); //<.>
        bikeStatusRepository.save(bikeStatus); //<.>
        //tag::UpdateEmitter[]
        updateEmitter.emit(q -> "findAll".equals(q.getQueryName()), bikeStatus); //<.>
        //end::UpdateEmitter[]
    }

    //end::BikeRegisteredEventHandler[]
    @EventHandler
    public void on(BikeRequestedEvent event) {
        bikeStatusRepository.findById(event.bikeId())
                            .map(bs -> {
                                bs.requestedBy(event.renter());
                                return bs;
                            })
                            .ifPresent(bs -> {
                                updateEmitter.emit(q -> "findAll".equals(q.getQueryName()), bs);
                                updateEmitter.emit(String.class, event.bikeId()::equals, bs);
                            });
    }

    @EventHandler
    public void on(BikeInUseEvent event) {
        bikeStatusRepository.findById(event.bikeId())
                            .map(bs -> {
                                bs.rentedBy(event.renter());
                                return bs;
                            })
                            .ifPresent(bs -> {
                                updateEmitter.emit(q -> "findAll".equals(q.getQueryName()), bs);
                                updateEmitter.emit(String.class, event.bikeId()::equals, bs);
                            });
    }

    @EventHandler
    public void on(BikeReturnedEvent event) {
        bikeStatusRepository.findById(event.bikeId())
                            .map(bs -> {
                                bs.returnedAt(event.location());
                                return bs;
                            })
                            .ifPresent(bs -> {
                                updateEmitter.emit(q -> "findAll".equals(q.getQueryName()), bs);
                                updateEmitter.emit(String.class, event.bikeId()::equals, bs);
                            });

    }

    @EventHandler
    public void on(RequestRejectedEvent event) {
        bikeStatusRepository.findById(event.bikeId())
                            .map(bs -> {
                                bs.returnedAt(bs.getLocation());
                                return bs;
                            })
                            .ifPresent(bs -> {
                                updateEmitter.emit(q -> "findAll".equals(q.getQueryName()), bs);
                                updateEmitter.emit(String.class, event.bikeId()::equals, bs);
                            });
    }

    //end::EventHandlers[]
    //tag::QueryHandlers[]
    //tag::findAllQueryHandler[]
    @QueryHandler(queryName = "findAll") //<.>
    public Iterable<BikeStatus> findAll() { // <.>
        return bikeStatusRepository.findAll(); //<.>
    }

    //end::findAllQueryHandler[]
    @QueryHandler(queryName = "findAvailable") //<.>
    public Iterable<BikeStatus> findAvailable(String bikeType) { //<.>
        return bikeStatusRepository.findAllByBikeTypeAndStatus(bikeType, RentalStatus.AVAILABLE);
    }

    @QueryHandler(queryName = "findOne") // <.>
    public BikeStatus findOne(String bikeId) { //<.>
        return bikeStatusRepository.findById(bikeId).orElse(null); //<.>
    }

    @QueryHandler
    public long countOfBikesByType(CountOfBikesByTypeQuery query) {
        return bikeStatusRepository.countBikeStatusesByBikeType(query.bikeType());
    }
    //end::QueryHandlers[]
}
//end::ClassDefinition[]