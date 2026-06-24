package io.axoniq.demo.bikerental.payment.handler;

import com.fasterxml.jackson.databind.JsonNode;
import io.axoniq.demo.bikerental.payment.PaymentStatusProjection;
import io.axoniq.demo.bikerental.payment.support.AxonSerializer;
import io.axoniq.demo.bikerental.payment.support.AxonServerMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Receives the event batches Axon Server pushes from the persistent stream backing the payment read
 * model's event handler. Events whose payload type is unknown here are skipped (the stream still
 * advances), so foreign events in the shared store don't stall the projection.
 */
@RestController
public class EventHandlerController {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerController.class);

    private final PaymentStatusProjection projection;
    private final AxonSerializer serializer;

    public EventHandlerController(PaymentStatusProjection projection, AxonSerializer serializer) {
        this.projection = projection;
        this.serializer = serializer;
    }

    @PostMapping("/axon/events/payment-status")
    public Mono<ResponseEntity<Void>> paymentStatus(@RequestBody JsonNode body) {
        return Mono.fromRunnable(() -> events(body).forEach(this::handle))
                   .subscribeOn(Schedulers.boundedElastic())
                   .thenReturn(ResponseEntity.ok().<Void>build());
    }

    private void handle(AxonServerMessages.Event event) {
        Object payload;
        try {
            payload = serializer.fromJson(event.payload(), event.type());
        } catch (Exception e) {
            logger.debug("Skipping event {} of unknown type {}", event.id(), event.type());
            return;
        }
        projection.on(payload);
    }

    /** Accepts either a bare JSON array of events or an object wrapping them under {@code events}. */
    private List<AxonServerMessages.Event> events(JsonNode body) {
        JsonNode array = body.isArray() ? body : body.get("events");
        return serializer.listFromJson(array, AxonServerMessages.Event.class);
    }
}
