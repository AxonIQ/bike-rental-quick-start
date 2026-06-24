package io.axoniq.demo.bikerental.rental.handler;

import io.axoniq.demo.bikerental.rental.query.BikeStatusProjection;
import io.axoniq.demo.bikerental.rental.support.AxonSerializer;
import io.axoniq.demo.bikerental.rental.support.AxonServerMessages;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Receives point-to-point queries that Axon Server routes to this application and answers them from
 * the {@link BikeStatusProjection} read model. The (possibly collection) result is serialized into
 * the {@code QueryResult} payload, which the dispatching side decodes as a single value or a list.
 */
@RestController
public class QueryHandlerController {

    private final BikeStatusProjection projection;
    private final AxonSerializer serializer;

    public QueryHandlerController(BikeStatusProjection projection, AxonSerializer serializer) {
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
