package io.axoniq.demo.bikerental.payment.handler;

import io.axoniq.demo.bikerental.payment.PaymentStatusProjection;
import io.axoniq.demo.bikerental.payment.support.AxonSerializer;
import io.axoniq.demo.bikerental.payment.support.AxonServerMessages;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Receives point-to-point queries that Axon Server routes to the payment service and answers them
 * from the {@link PaymentStatusProjection} read model.
 */
@RestController
public class QueryHandlerController {

    private final PaymentStatusProjection projection;
    private final AxonSerializer serializer;

    public QueryHandlerController(PaymentStatusProjection projection, AxonSerializer serializer) {
        this.projection = projection;
        this.serializer = serializer;
    }

    @PostMapping("/axon/query")
    public Mono<ResponseEntity<Object>> handle(@RequestBody AxonServerMessages.Query query) {
        return Mono.fromCallable(() -> projection.handle(query))
                   .subscribeOn(Schedulers.boundedElastic())
                   .map(result -> result(query.id(), result))
                   .defaultIfEmpty(result(query.id(), null))
                   .onErrorResume(error -> Mono.just(
                           ResponseEntity.status(500).body(Map.of("message", String.valueOf(error.getMessage())))));
    }

    private ResponseEntity<Object> result(String id, Object result) {
        return ResponseEntity.ok(new AxonServerMessages.QueryResult(
                id, null, null, null, result == null ? null : serializer.toJson(result)));
    }
}
