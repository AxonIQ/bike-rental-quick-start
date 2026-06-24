package io.axoniq.demo.bikerental.rental.handler;

import com.fasterxml.jackson.databind.JsonNode;
import io.axoniq.demo.bikerental.rental.paymentsaga.PaymentSaga;
import io.axoniq.demo.bikerental.rental.query.BikeStatusProjection;
import io.axoniq.demo.bikerental.rental.support.AxonSerializer;
import io.axoniq.demo.bikerental.rental.support.AxonServerMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.Consumer;

/**
 * Receives the event batches that Axon Server pushes from the persistent streams backing this
 * application's two event handlers (registered by {@code IntegrationRegistrar}): one feeds the bike
 * read model, the other drives the payment saga. This is the HTTP-branch equivalent of the gRPC
 * branch's streaming event processors - except the stream position is tracked by Axon Server, so
 * there is no token to persist here.
 * <p>
 * Events whose payload type is unknown to this application are skipped (the stream still advances),
 * so foreign events in the shared store don't stall a handler.
 */
@RestController
public class EventHandlerController {

    private static final Logger logger = LoggerFactory.getLogger(EventHandlerController.class);

    private final BikeStatusProjection projection;
    private final PaymentSaga saga;
    private final AxonSerializer serializer;

    public EventHandlerController(BikeStatusProjection projection, PaymentSaga saga, AxonSerializer serializer) {
        this.projection = projection;
        this.saga = saga;
        this.serializer = serializer;
    }

    @PostMapping("/axon/events/bike-projection")
    public Mono<ResponseEntity<Void>> bikeProjection(@RequestBody JsonNode body) {
        return process(body, projection::on);
    }

    @PostMapping("/axon/events/payment-saga")
    public Mono<ResponseEntity<Void>> paymentSaga(@RequestBody JsonNode body) {
        return process(body, saga::accept);
    }

    private Mono<ResponseEntity<Void>> process(JsonNode body, Consumer<Object> handler) {
        return Mono.fromRunnable(() -> events(body).forEach(event -> handle(event, handler)))
                   .subscribeOn(Schedulers.boundedElastic())
                   .thenReturn(ResponseEntity.ok().<Void>build());
    }

    private void handle(AxonServerMessages.Event event, Consumer<Object> handler) {
        Object payload;
        try {
            payload = serializer.fromJson(event.payload(), event.type());
        } catch (Exception e) {
            logger.debug("Skipping event {} of unknown type {}", event.id(), event.type());
            return;
        }
        handler.accept(payload);
    }

    /** Accepts either a bare JSON array of events or an object wrapping them under {@code events}. */
    private List<AxonServerMessages.Event> events(JsonNode body) {
        JsonNode array = body.isArray() ? body : body.get("events");
        return serializer.listFromJson(array, AxonServerMessages.Event.class);
    }
}
