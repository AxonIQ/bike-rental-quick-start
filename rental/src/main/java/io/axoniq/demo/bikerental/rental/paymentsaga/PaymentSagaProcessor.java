package io.axoniq.demo.bikerental.rental.paymentsaga;

import io.axoniq.axonserver.connector.event.EventChannel;
import io.axoniq.axonserver.connector.event.EventStream;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentConfirmedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentPreparedEvent;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentRejectedEvent;
import io.axoniq.demo.bikerental.coreapi.rental.BikeRequestedEvent;
import io.axoniq.demo.bikerental.coreapi.rental.RequestRejectedEvent;
import io.axoniq.demo.bikerental.rental.support.AxonSerializer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Feeds events to the {@link PaymentSaga}. Like the original saga processor (which started at the
 * head token), this begins at the current end of the stream so only new bike requests start a saga.
 * Saga state is in-memory, so there is no token to persist - on restart we simply pick up new events.
 */
@Component
public class PaymentSagaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentSagaProcessor.class);
    private static final int BUFFER_SIZE = 100;
    private static final long POLL_TIMEOUT_MILLIS = 500;

    private final EventChannel eventChannel;
    private final PaymentSaga saga;
    private final AxonSerializer serializer;

    private volatile boolean running = true;
    private EventStream eventStream;
    private Thread worker;

    public PaymentSagaProcessor(EventChannel eventChannel, PaymentSaga saga, AxonSerializer serializer) {
        this.eventChannel = eventChannel;
        this.saga = saga;
        this.serializer = serializer;
    }

    @PostConstruct
    public void start() throws Exception {
        long head = eventChannel.getLastToken().get();
        this.eventStream = eventChannel.openStream(head, BUFFER_SIZE);
        this.worker = new Thread(this::run, "payment-saga");
        this.worker.setDaemon(true);
        this.worker.start();
        logger.info("Started payment saga from head token {}", head);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (eventStream != null) {
            eventStream.close();
        }
        if (worker != null) {
            worker.interrupt();
        }
    }

    private void run() {
        while (running && !eventStream.isClosed()) {
            try {
                EventWithToken event = eventStream.nextIfAvailable(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                if (event != null) {
                    dispatch(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                logger.warn("Error while processing saga events", e);
            }
        }
    }

    private void dispatch(EventWithToken event) {
        Object payload;
        try {
            payload = serializer.deserialize(event.getEvent().getPayload());
        } catch (Exception e) {
            return;
        }
        if (payload instanceof BikeRequestedEvent e) {
            saga.on(e);
        } else if (payload instanceof PaymentPreparedEvent e) {
            saga.on(e);
        } else if (payload instanceof PaymentConfirmedEvent e) {
            saga.on(e);
        } else if (payload instanceof PaymentRejectedEvent e) {
            saga.on(e);
        } else if (payload instanceof RequestRejectedEvent e) {
            saga.on(e);
        }
    }
}
