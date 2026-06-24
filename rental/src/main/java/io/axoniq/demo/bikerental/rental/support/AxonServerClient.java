package io.axoniq.demo.bikerental.rental.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thin client over the Axon Server <em>Integration</em> HTTP API. This is the HTTP equivalent of the
 * gRPC branch's command/query/event channels: it is used to send commands and queries, publish and
 * read events, and to register this application's handler endpoints so Axon Server pushes messages
 * to them.
 * <p>
 * All calls target a single {@code context} (default) and are plain blocking HTTP, so callers run
 * them off the request/event-loop threads (see {@code CommandDispatcher}, the handler controllers).
 */
@Component
public class AxonServerClient {

    private static final Logger logger = LoggerFactory.getLogger(AxonServerClient.class);
    // The media type the Axon Server event-publish endpoint consumes (per its OpenAPI definition).
    private static final MediaType EVENT_LIST_JSON = MediaType.parseMediaType("application/vnd.axoniq.event.list+json");

    private final RestClient restClient;
    private final String context;

    public AxonServerClient(@Value("${axon.axonserver.http-url:http://localhost:8024}") String axonServerUrl,
                            @Value("${axon.axonserver.context:default}") String context) {
        this.restClient = RestClient.builder().baseUrl(axonServerUrl).build();
        this.context = context;
    }

    // ---------------------------------------------------------------------------------------------
    // Messaging: send commands / queries, publish & read events
    // ---------------------------------------------------------------------------------------------

    public AxonServerMessages.CommandResult sendCommand(AxonServerMessages.Command command) {
        return restClient.post()
                         .uri(uri -> uri.path("/v2/commands").queryParam("context", context).build())
                         .contentType(MediaType.APPLICATION_JSON)
                         .body(command)
                         .retrieve()
                         .onStatus(status -> status.isError(), (req, res) -> {
                             throw new CommandExecutionException(
                                     "AXONIQ-HTTP", readError(res.getStatusText(), res.getBody()));
                         })
                         .body(AxonServerMessages.CommandResult.class);
    }

    public AxonServerMessages.QueryResult sendQuery(AxonServerMessages.Query query) {
        return restClient.post()
                         .uri(uri -> uri.path("/v2/queries").queryParam("context", context).build())
                         .contentType(MediaType.APPLICATION_JSON)
                         .body(query)
                         .retrieve()
                         .onStatus(status -> status.isError(), (req, res) -> {
                             throw new QueryExecutionException(readError(res.getStatusText(), res.getBody()));
                         })
                         .body(AxonServerMessages.QueryResult.class);
    }

    /** Appends events in a single transaction (POST /v2/events with the wrapped JSON list form). */
    public void appendEvents(List<AxonServerMessages.Event> events) {
        if (events.isEmpty()) {
            return;
        }
        restClient.post()
                  .uri(uri -> uri.path("/v2/events").queryParam("context", context).build())
                  .contentType(EVENT_LIST_JSON)
                  .body(events)
                  .retrieve()
                  .toBodilessEntity();
    }

    /**
     * Reads an aggregate's event stream (ascending sequence), used to rebuild a decision model.
     * A not-yet-existing aggregate (no events) yields an empty list.
     */
    public List<AxonServerMessages.Event> readAggregateEvents(String aggregateId) {
        try {
            AxonServerMessages.Event[] events = restClient.get()
                    .uri(uri -> uri.path("/v2/aggregates/{id}/events")
                                   .queryParam("context", context)
                                   .build(aggregateId))
                    .retrieve()
                    .body(AxonServerMessages.Event[].class);
            return events == null ? List.of() : List.of(events);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return List.of();
            }
            throw e;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Integration registration: tell Axon Server where to push commands/queries/events
    // ---------------------------------------------------------------------------------------------

    /** Registers (or looks up an existing) endpoint and returns its id. */
    public String registerEndpoint(Map<String, Object> endpoint) {
        String name = (String) endpoint.get("name");
        Optional<String> existing = findEndpointId(name);
        if (existing.isPresent()) {
            logger.info("Integration endpoint '{}' already registered ({})", name, existing.get());
            return existing.get();
        }
        restClient.post()
                  .uri(uri -> uri.path("/v2/endpoints").queryParam("context", context).build())
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(endpoint)
                  .retrieve()
                  .toBodilessEntity();
        return findEndpointId(name).orElseThrow(
                () -> new IllegalStateException("Endpoint '" + name + "' was not found after registration"));
    }

    public Optional<String> findEndpointId(String name) {
        List<Map<String, Object>> endpoints = restClient.get()
                .uri("/v2/endpoints")
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {
                });
        if (endpoints == null) {
            return Optional.empty();
        }
        return endpoints.stream()
                        .filter(e -> name.equals(e.get("name")) && context.equals(e.get("context")))
                        .map(e -> String.valueOf(e.get("id")))
                        .findFirst();
    }

    public void registerCommandHandler(String endpointId, Map<String, Object> handler) {
        registerHandler(endpointId, "commandHandlers", handler);
    }

    public void registerQueryHandler(String endpointId, Map<String, Object> handler) {
        registerHandler(endpointId, "queryHandlers", handler);
    }

    public void registerEventHandler(String endpointId, Map<String, Object> handler) {
        registerHandler(endpointId, "eventHandlers", handler);
    }

    private void registerHandler(String endpointId, String kind, Map<String, Object> handler) {
        try {
            restClient.post()
                      .uri(uri -> uri.path("/v2/endpoints/{endpoint}/" + kind)
                                     .queryParam("context", context)
                                     .build(endpointId))
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(handler)
                      .retrieve()
                      .toBodilessEntity();
            logger.info("Registered {} '{}'", kind, handler.get("name"));
        } catch (RestClientResponseException e) {
            // Most likely the handler is already registered from a previous run - safe to ignore.
            logger.info("Skipping {} '{}' registration: {}", kind, handler.get("name"), e.getStatusText());
        }
    }

    private static String readError(String statusText, java.io.InputStream body) {
        try {
            byte[] bytes = body.readAllBytes();
            return bytes.length == 0 ? statusText : new String(bytes);
        } catch (Exception e) {
            return statusText;
        }
    }
}
