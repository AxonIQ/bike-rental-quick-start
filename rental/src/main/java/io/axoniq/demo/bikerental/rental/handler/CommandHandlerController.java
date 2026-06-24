package io.axoniq.demo.bikerental.rental.handler;

import io.axoniq.demo.bikerental.rental.command.BikeCommandHandler;
import io.axoniq.demo.bikerental.rental.support.AxonServerMessages;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * Receives commands that Axon Server routes to this application over the Integration HTTP API (the
 * endpoint registered by {@code IntegrationRegistrar}). It hands the command to the
 * {@link BikeCommandHandler} and returns a command result; a failure is reported as a 500 so Axon
 * Server relays it to the original sender.
 */
@RestController
public class CommandHandlerController {

    private final BikeCommandHandler commandHandler;

    public CommandHandlerController(BikeCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    @PostMapping("/axon/command")
    public Mono<ResponseEntity<Object>> handle(@RequestBody AxonServerMessages.Command command) {
        return Mono.fromFuture(commandHandler.handle(command))
                   .map(payload -> ok(command.id(), payload))
                   .defaultIfEmpty(ok(command.id(), null))
                   .onErrorResume(error -> Mono.just(failure(error)));
    }

    private static ResponseEntity<Object> ok(String id, com.fasterxml.jackson.databind.JsonNode payload) {
        return ResponseEntity.ok(new AxonServerMessages.CommandResult(id, null, null, null, payload));
    }

    private static ResponseEntity<Object> failure(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause() : error;
        return ResponseEntity.status(500).body(Map.of("message", String.valueOf(cause.getMessage())));
    }
}
