package io.axoniq.demo.bikerental.payment.eventhandling;

import io.axoniq.axonserver.connector.event.EventChannel;
import io.axoniq.axonserver.connector.event.EventStream;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.axoniq.demo.bikerental.payment.PaymentStatusProjection;
import io.axoniq.demo.bikerental.payment.support.AxonSerializer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Drives the payment read model by reading the Axon Server event stream directly: a single
 * background thread that resumes from the last persisted {@link ProjectionToken}, applies each event
 * to the projection, and advances the token in the same transaction.
 */
@Component
public class PaymentStatusEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PaymentStatusEventProcessor.class);
    private static final String PROCESSOR_NAME = "payment-status-projection";
    private static final int BUFFER_SIZE = 100;
    private static final long POLL_TIMEOUT_MILLIS = 500;

    private final EventChannel eventChannel;
    private final PaymentStatusProjection projection;
    private final ProjectionTokenRepository tokenRepository;
    private final AxonSerializer serializer;

    @Autowired
    @Lazy
    private PaymentStatusEventProcessor self;

    private volatile boolean running = true;
    private EventStream eventStream;
    private Thread worker;

    public PaymentStatusEventProcessor(EventChannel eventChannel,
                                       PaymentStatusProjection projection,
                                       ProjectionTokenRepository tokenRepository,
                                       AxonSerializer serializer) {
        this.eventChannel = eventChannel;
        this.projection = projection;
        this.tokenRepository = tokenRepository;
        this.serializer = serializer;
    }

    @PostConstruct
    public void start() {
        long startToken = tokenRepository.findById(PROCESSOR_NAME)
                                         .map(ProjectionToken::getPosition)
                                         .orElse(-1L);
        this.eventStream = eventChannel.openStream(startToken, BUFFER_SIZE);
        this.worker = new Thread(this::run, "payment-status-projection");
        this.worker.setDaemon(true);
        this.worker.start();
        logger.info("Started payment status projection from token {}", startToken);
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
                    self.handleEvent(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                logger.warn("Error while processing payment events", e);
            }
        }
    }

    @Transactional
    public void handleEvent(EventWithToken event) {
        try {
            Object payload = serializer.deserialize(event.getEvent().getPayload());
            projection.on(payload);
        } catch (Exception e) {
            logger.debug("Skipping event at token {}: {}", event.getToken(), e.getMessage());
        }
        tokenRepository.save(new ProjectionToken(PROCESSOR_NAME, event.getToken()));
    }
}
