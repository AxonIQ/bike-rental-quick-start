package io.axoniq.demo.bikerental.rental.paymentsaga;

import io.axoniq.demo.bikerental.coreapi.payment.PaymentConfirmedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentPreparedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentRejectedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PreparePaymentCommand;
import io.axoniq.demo.bikerental.coreapi.payment.RejectPaymentCommand;
import io.axoniq.demo.bikerental.coreapi.rental.ApproveRequestCommand;
import io.axoniq.demo.bikerental.coreapi.rental.BikeRequestedEvent;
import io.axoniq.demo.bikerental.coreapi.rental.RejectRequestCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RequestRejectedEvent;
import io.axoniq.demo.bikerental.rental.support.CommandDispatcher;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates payment for a bike request. This used to be an Axon Framework {@code @Saga}; here it
 * is an in-memory state machine driven by {@link PaymentSagaProcessor} and using a
 * {@link ScheduledExecutorService} for the deadlines (replacing the {@code DeadlineManager}).
 * <p>
 * Flow: a bike request kicks off a payment; once prepared we give the renter 30s to pay before the
 * payment is auto-rejected; a confirmed payment approves the request, a rejected payment rejects it.
 * Saga instances are keyed by the payment reference, with a secondary index on bike id.
 */
@Component
public class PaymentSaga {

    private static final Logger logger = LoggerFactory.getLogger(PaymentSaga.class);
    private static final int PAYMENT_AMOUNT = 10;
    private static final Duration CANCEL_AFTER = Duration.ofSeconds(30);
    private static final Duration RETRY_AFTER = Duration.ofSeconds(5);

    private final CommandDispatcher commandDispatcher;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ConcurrentHashMap<String, Instance> byReference = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> referenceByBikeId = new ConcurrentHashMap<>();

    public PaymentSaga(CommandDispatcher commandDispatcher) {
        this.commandDispatcher = commandDispatcher;
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    /**
     * Entry point used by the event handler controller: routes an event to the matching {@code on}
     * method. Events the saga does not care about are ignored.
     */
    public void accept(Object event) {
        if (event instanceof BikeRequestedEvent e) {
            on(e);
        } else if (event instanceof PaymentPreparedEvent e) {
            on(e);
        } else if (event instanceof PaymentConfirmedEvent e) {
            on(e);
        } else if (event instanceof PaymentRejectedEvent e) {
            on(e);
        } else if (event instanceof RequestRejectedEvent e) {
            on(e);
        }
    }

    public void on(BikeRequestedEvent event) {
        Instance instance = new Instance(event.bikeId(), event.renter(), event.rentalReference());
        byReference.put(event.rentalReference(), instance);
        referenceByBikeId.put(event.bikeId(), event.rentalReference());
        preparePayment(instance);
    }

    public void on(PaymentPreparedEvent event) {
        Instance instance = byReference.get(event.paymentReference());
        if (instance == null) {
            return;
        }
        synchronized (instance) {
            instance.paymentId = event.paymentId();
            instance.cancelDeadline = scheduler.schedule(
                    () -> commandDispatcher.send(new RejectPaymentCommand(event.paymentId())),
                    CANCEL_AFTER.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public void on(PaymentConfirmedEvent event) {
        Instance instance = byReference.get(event.paymentReference());
        if (instance == null) {
            return;
        }
        commandDispatcher.send(new ApproveRequestCommand(instance.bikeId, instance.renter));
        end(instance);
    }

    public void on(PaymentRejectedEvent event) {
        Instance instance = byReference.get(event.paymentReference());
        if (instance == null) {
            return;
        }
        commandDispatcher.send(new RejectRequestCommand(instance.bikeId, instance.renter));
    }

    public void on(RequestRejectedEvent event) {
        String reference = referenceByBikeId.get(event.bikeId());
        if (reference == null) {
            return;
        }
        Instance instance = byReference.get(reference);
        if (instance != null) {
            end(instance);
        }
    }

    private void preparePayment(Instance instance) {
        commandDispatcher.send(new PreparePaymentCommand(PAYMENT_AMOUNT, instance.paymentReference))
                         .whenComplete((result, error) -> {
                             if (error != null && byReference.containsKey(instance.paymentReference)) {
                                 logger.warn("Preparing payment for {} failed, retrying in {}s",
                                             instance.paymentReference, RETRY_AFTER.toSeconds());
                                 scheduler.schedule(() -> preparePayment(instance),
                                                    RETRY_AFTER.toMillis(), TimeUnit.MILLISECONDS);
                             }
                         });
    }

    private void end(Instance instance) {
        synchronized (instance) {
            if (instance.cancelDeadline != null) {
                instance.cancelDeadline.cancel(false);
            }
        }
        byReference.remove(instance.paymentReference);
        referenceByBikeId.remove(instance.bikeId);
    }

    private static final class Instance {

        private final String bikeId;
        private final String renter;
        private final String paymentReference;
        private volatile String paymentId;
        private volatile ScheduledFuture<?> cancelDeadline;

        private Instance(String bikeId, String renter, String paymentReference) {
            this.bikeId = bikeId;
            this.renter = renter;
            this.paymentReference = paymentReference;
        }
    }
}
