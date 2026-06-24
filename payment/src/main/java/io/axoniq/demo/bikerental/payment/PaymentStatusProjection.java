package io.axoniq.demo.bikerental.payment;

import io.axoniq.axonserver.connector.ErrorCategory;
import io.axoniq.axonserver.connector.Registration;
import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.connector.query.QueryChannel;
import io.axoniq.axonserver.connector.query.QueryDefinition;
import io.axoniq.axonserver.connector.query.QueryHandler;
import io.axoniq.axonserver.grpc.query.QueryRequest;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import io.axoniq.axonserver.grpc.query.SubscriptionQuery;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentConfirmedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentPreparedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentRejectedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatusNamedQueries;
import io.axoniq.demo.bikerental.payment.query.SubscriptionRegistry;
import io.axoniq.demo.bikerental.payment.support.AxonSerializer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus.Status.APPROVED;
import static io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus.Status.PENDING;
import static io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus.Status.REJECTED;

/**
 * The payment read model. It answers queries through the raw Axon Server {@link QueryChannel} and is
 * fed events by {@link io.axoniq.demo.bikerental.payment.eventhandling.PaymentStatusEventProcessor}.
 * The {@code getPaymentId} subscription query lets a renter wait for a payment id to appear: when a
 * payment is prepared, the new id is emitted to subscribers waiting on that payment reference.
 */
@Component
public class PaymentStatusProjection implements QueryHandler {

    private static final String GET_STATUS = "getStatus";

    private final PaymentStatusRepository repository;
    private final QueryChannel queryChannel;
    private final AxonSerializer serializer;
    private final SubscriptionRegistry subscriptions = new SubscriptionRegistry();

    private Registration registration;

    public PaymentStatusProjection(PaymentStatusRepository repository,
                                   QueryChannel queryChannel,
                                   AxonSerializer serializer) {
        this.repository = repository;
        this.queryChannel = queryChannel;
        this.serializer = serializer;
    }

    @PostConstruct
    public void start() {
        this.registration = queryChannel.registerQueryHandler(
                this,
                new QueryDefinition(GET_STATUS, PaymentStatus.class.getName()),
                new QueryDefinition(PaymentStatusNamedQueries.GET_PAYMENT_ID, String.class.getName()),
                new QueryDefinition(PaymentStatusNamedQueries.GET_ALL_PAYMENTS, PaymentStatus.class.getName()));
    }

    @PreDestroy
    public void stop() {
        if (registration != null) {
            registration.cancel();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Query handling
    // ---------------------------------------------------------------------------------------------

    @Override
    public void handle(QueryRequest query, ReplyChannel<QueryResponse> responseHandler) {
        try {
            switch (query.getQuery()) {
                case GET_STATUS -> replyOne(query, responseHandler,
                                            repository.findById(payloadAsString(query)).orElse(null));
                case PaymentStatusNamedQueries.GET_PAYMENT_ID -> replyOne(query, responseHandler, getPaymentId(query));
                case PaymentStatusNamedQueries.GET_ALL_PAYMENTS -> replyList(query, responseHandler, findByStatus(query));
                default -> responseHandler.completeWithError(ErrorCategory.NO_HANDLER_FOR_QUERY,
                                                             "No handler for query " + query.getQuery());
            }
        } catch (Exception e) {
            responseHandler.completeWithError(ErrorCategory.QUERY_EXECUTION_ERROR, String.valueOf(e.getMessage()));
        }
    }

    private String getPaymentId(QueryRequest query) {
        return repository.findByReferenceAndStatus(payloadAsString(query), PENDING)
                         .map(PaymentStatus::getId)
                         .orElse(null);
    }

    private List<PaymentStatus> findByStatus(QueryRequest query) {
        if (!query.hasPayload()) {
            return toList(repository.findAll());
        }
        PaymentStatus.Status status = serializer.deserialize(query.getPayload(), PaymentStatus.Status.class);
        return status == null ? toList(repository.findAll()) : repository.findAllByStatus(status);
    }

    // ---------------------------------------------------------------------------------------------
    // Subscription query handling
    // ---------------------------------------------------------------------------------------------

    @Override
    public Registration registerSubscriptionQuery(SubscriptionQuery query, UpdateHandler updateHandler) {
        if (PaymentStatusNamedQueries.GET_PAYMENT_ID.equals(query.getQueryRequest().getQuery())) {
            String reference = serializer.deserialize(query.getQueryRequest().getPayload(), String.class);
            return subscriptions.register(PaymentStatusNamedQueries.GET_PAYMENT_ID, reference, updateHandler);
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Event handling (called by the event processor, transactionally)
    // ---------------------------------------------------------------------------------------------

    @Transactional
    public void on(Object event) {
        if (event instanceof PaymentPreparedEvent e) {
            repository.save(new PaymentStatus(e.paymentId(), e.amount(), e.paymentReference()));
            QueryUpdate update = QueryUpdate.newBuilder()
                                            .setMessageIdentifier(UUID.randomUUID().toString())
                                            .setPayload(serializer.serialize(e.paymentId()))
                                            .build();
            subscriptions.emit(PaymentStatusNamedQueries.GET_PAYMENT_ID, e.paymentReference(), update);
        } else if (event instanceof PaymentConfirmedEvent e) {
            repository.findById(e.paymentId()).ifPresent(status -> {
                status.setStatus(APPROVED);
                repository.save(status);
            });
        } else if (event instanceof PaymentRejectedEvent e) {
            repository.findById(e.paymentId()).ifPresent(status -> {
                status.setStatus(REJECTED);
                repository.save(status);
            });
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private String payloadAsString(QueryRequest query) {
        return serializer.deserialize(query.getPayload(), String.class);
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

    private void replyList(QueryRequest query, ReplyChannel<QueryResponse> responseHandler, List<PaymentStatus> results) {
        responseHandler.sendLast(QueryResponse.newBuilder()
                                              .setMessageIdentifier(UUID.randomUUID().toString())
                                              .setRequestIdentifier(query.getMessageIdentifier())
                                              .setPayload(serializer.serialize(results))
                                              .build());
    }

    private List<PaymentStatus> toList(Iterable<PaymentStatus> results) {
        List<PaymentStatus> list = new ArrayList<>();
        results.forEach(list::add);
        return list;
    }
}
