package io.axoniq.demo.bikerental.payment;

import io.axoniq.demo.bikerental.coreapi.payment.PaymentConfirmedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentPreparedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentRejectedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatusNamedQueries;
import io.axoniq.demo.bikerental.payment.support.AxonSerializer;
import io.axoniq.demo.bikerental.payment.support.AxonServerMessages;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus.Status.APPROVED;
import static io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus.Status.PENDING;
import static io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus.Status.REJECTED;

/**
 * The payment read model. It answers point-to-point queries (invoked by the
 * {@code QueryHandlerController} when Axon Server routes a query to us) and is fed events by the
 * {@code EventHandlerController}. The {@code getPaymentId} query lets a renter find the payment id
 * once a payment for its reference has been prepared.
 */
@Component
public class PaymentStatusProjection {

    static final String GET_STATUS = "getStatus";

    private final PaymentStatusRepository repository;
    private final AxonSerializer serializer;

    public PaymentStatusProjection(PaymentStatusRepository repository, AxonSerializer serializer) {
        this.repository = repository;
        this.serializer = serializer;
    }

    // ---------------------------------------------------------------------------------------------
    // Query handling
    // ---------------------------------------------------------------------------------------------

    public Object handle(AxonServerMessages.Query query) {
        return switch (query.name()) {
            case GET_STATUS -> repository.findById(payloadAsString(query)).orElse(null);
            case PaymentStatusNamedQueries.GET_PAYMENT_ID -> getPaymentId(query);
            case PaymentStatusNamedQueries.GET_ALL_PAYMENTS -> findByStatus(query);
            default -> throw new IllegalArgumentException("No handler for query " + query.name());
        };
    }

    private String getPaymentId(AxonServerMessages.Query query) {
        return repository.findByReferenceAndStatus(payloadAsString(query), PENDING)
                         .map(PaymentStatus::getId)
                         .orElse(null);
    }

    private List<PaymentStatus> findByStatus(AxonServerMessages.Query query) {
        PaymentStatus.Status status = serializer.fromJson(query.payload(), PaymentStatus.Status.class);
        return status == null ? toList(repository.findAll()) : repository.findAllByStatus(status);
    }

    // ---------------------------------------------------------------------------------------------
    // Event handling (called by the event handler controller, transactionally)
    // ---------------------------------------------------------------------------------------------

    @Transactional
    public void on(Object event) {
        if (event instanceof PaymentPreparedEvent e) {
            repository.save(new PaymentStatus(e.paymentId(), e.amount(), e.paymentReference()));
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

    private String payloadAsString(AxonServerMessages.Query query) {
        return serializer.fromJson(query.payload(), String.class);
    }

    private List<PaymentStatus> toList(Iterable<PaymentStatus> results) {
        List<PaymentStatus> list = new ArrayList<>();
        results.forEach(list::add);
        return list;
    }
}
