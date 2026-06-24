package io.axoniq.demo.bikerental.payment;

import io.axoniq.demo.bikerental.coreapi.payment.ConfirmPaymentCommand;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentConfirmedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentPreparedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentRejectedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.RejectPaymentCommand;

import java.util.List;

/**
 * The Payment decision model. Formerly an Axon Framework {@code @Aggregate}; now a plain object
 * that {@link PaymentCommandHandler} rebuilds by replaying events ({@link #apply(Object)}) before
 * asking it to decide on a command.
 */
public class Payment {

    private String id;
    private boolean closed;
    private String paymentReference;
    private long lastSequence = -1;

    public void apply(Object event) {
        if (event instanceof PaymentPreparedEvent e) {
            this.id = e.paymentId();
            this.paymentReference = e.paymentReference();
        } else if (event instanceof PaymentConfirmedEvent e) {
            this.closed = true;
        } else if (event instanceof PaymentRejectedEvent e) {
            this.closed = true;
        }
    }

    public List<Object> decideOnConfirm(ConfirmPaymentCommand command) {
        if (closed) {
            return List.of();
        }
        return List.of(new PaymentConfirmedEvent(command.paymentId(), paymentReference));
    }

    public List<Object> decideOnReject(RejectPaymentCommand command) {
        if (closed) {
            return List.of();
        }
        return List.of(new PaymentRejectedEvent(command.paymentId(), paymentReference));
    }

    public long lastSequence() {
        return lastSequence;
    }

    public void setLastSequence(long lastSequence) {
        this.lastSequence = lastSequence;
    }
}
