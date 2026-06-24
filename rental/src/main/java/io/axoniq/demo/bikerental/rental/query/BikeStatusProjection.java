package io.axoniq.demo.bikerental.rental.query;

import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.connector.query.QueryChannel;
import io.axoniq.axonserver.connector.query.QueryDefinition;
import io.axoniq.axonserver.connector.query.QueryHandler;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The read model for bikes. It answers point-to-point and subscription queries through the raw
 * Axon Server {@link QueryChannel} (implementing {@link QueryHandler} directly), and it is fed
 * events by {@link BikeStatusEventProcessor}. When an event changes a {@link BikeStatus}, matching
 * subscription queries receive a {@link QueryUpdate} via the {@link SubscriptionRegistry} - the
 * replacement for Axon Framework's {@code QueryUpdateEmitter}.
 */
@Component
public class BikeStatusProjection implements QueryHandler {

    private final BikeStatusRepository repository;
    private final QueryChannel queryChannel;
    private final AxonSerializer serializer;
    private final SubscriptionRegistry subscriptions = new SubscriptionRegistry();

    private Registration registration;

    public BikeStatusProjection(BikeStatusRepository repository, QueryChannel queryChannel, AxonSerializer serializer) {
        this.repository = repository;
        this.queryChannel = queryChannel;
        this.serializer = serializer;
    }

    @PostConstruct
    public void start() {
        this.registration = queryChannel.registerQueryHandler(
                this,
                new QueryDefinition(BikeStatusNamedQueries.FIND_ALL, BikeStatus.class.getName()),
                new QueryDefinition(BikeStatusNamedQueries.FIND_ONE, BikeStatus.class.getName()),
                new QueryDefinition(BikeStatusNamedQueries.FIND_AVAILABLE, BikeStatus.class.getName()),
                new QueryDefinition(CountOfBikesByTypeQuery.class.getName(), Long.class.getName()));
    }

    @PreDestroy
    public void stop() {
        if (registration != null) {
            registration.cancel();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Query handling (point-to-point)
    // ---------------------------------------------------------------------------------------------

    @Override
    public void handle(QueryRequest query, ReplyChannel<QueryResponse> responseHandler) {
        try {
            switch (query.getQuery()) {
                case BikeStatusNamedQueries.FIND_ALL ->
                        replyList(query, responseHandler, repository.findAll());
                case BikeStatusNamedQueries.FIND_AVAILABLE ->
                        replyList(query, responseHandler, findAvailable(payloadAsString(query)));
                case BikeStatusNamedQueries.FIND_ONE ->
                        replyOne(query, responseHandler, repository.findById(payloadAsString(query)).orElse(null));
                default -> {
                    if (CountOfBikesByTypeQuery.class.getName().equals(query.getQuery())) {
                        CountOfBikesByTypeQuery payload =
                                serializer.deserialize(query.getPayload(), CountOfBikesByTypeQuery.class);
                        replyOne(query, responseHandler, repository.countBikeStatusesByBikeType(payload.bikeType()));
                    } else {
                        responseHandler.completeWithError(
                                io.axoniq.axonserver.connector.ErrorCategory.NO_HANDLER_FOR_QUERY,
                                "No handler for query " + query.getQuery());
                    }
                }
            }
        } catch (Exception e) {
            responseHandler.completeWithError(
                    io.axoniq.axonserver.connector.ErrorCategory.QUERY_EXECUTION_ERROR,
                    String.valueOf(e.getMessage()));
        }
    }

    private List<BikeStatus> findAvailable(String bikeType) {
        return repository.findAllByBikeTypeAndStatus(bikeType, RentalStatus.AVAILABLE);
    }

    // ---------------------------------------------------------------------------------------------
    // Subscription query handling
    // ---------------------------------------------------------------------------------------------

    @Override
    public Registration registerSubscriptionQuery(SubscriptionQuery query, UpdateHandler updateHandler) {
        String queryName = query.getQueryRequest().getQuery();
        if (BikeStatusNamedQueries.FIND_ALL.equals(queryName)) {
            return subscriptions.register(BikeStatusNamedQueries.FIND_ALL, null, updateHandler);
        }
        if (BikeStatusNamedQueries.FIND_ONE.equals(queryName)) {
            String bikeId = serializer.deserialize(query.getQueryRequest().getPayload(), String.class);
            return subscriptions.register(BikeStatusNamedQueries.FIND_ONE, bikeId, updateHandler);
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Event handling (called by BikeStatusEventProcessor, transactionally)
    // ---------------------------------------------------------------------------------------------

    @Transactional
    public void on(Object event) {
        if (event instanceof BikeRegisteredEvent e) {
            BikeStatus status = new BikeStatus(e.bikeId(), e.bikeType(), e.location());
            repository.save(status);
            emit(status);
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
        QueryUpdate update = QueryUpdate.newBuilder()
                                        .setMessageIdentifier(UUID.randomUUID().toString())
                                        .setPayload(serializer.serialize(status))
                                        .build();
        subscriptions.emit(BikeStatusNamedQueries.FIND_ALL, null, update);
        subscriptions.emit(BikeStatusNamedQueries.FIND_ONE, status.getBikeId(), update);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private String payloadAsString(QueryRequest query) {
        return serializer.deserialize(query.getPayload(), String.class);
    }

    private void replyList(QueryRequest query, ReplyChannel<QueryResponse> responseHandler, Iterable<BikeStatus> results) {
        List<BikeStatus> list = (results instanceof List<BikeStatus> l) ? l : toList(results);
        responseHandler.sendLast(QueryResponse.newBuilder()
                                              .setMessageIdentifier(UUID.randomUUID().toString())
                                              .setRequestIdentifier(query.getMessageIdentifier())
                                              .setPayload(serializer.serialize(list))
                                              .build());
    }

    private void replyOne(QueryRequest query, ReplyChannel<QueryResponse> responseHandler, Object result) {
        QueryResponse.Builder response = QueryResponse.newBuilder()
                                                      .setMessageIdentifier(UUID.randomUUID().toString())
                                                      .setRequestIdentifier(query.getMessageIdentifier());
        if (result != null) {
            response.setPayload(serializer.serialize(result));
        }
        responseHandler.sendLast(response.build());
    }

    private List<BikeStatus> toList(Iterable<BikeStatus> results) {
        java.util.ArrayList<BikeStatus> list = new java.util.ArrayList<>();
        results.forEach(list::add);
        return list;
    }
}
