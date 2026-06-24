package io.axoniq.demo.bikerental.rental.support;

import io.axoniq.demo.bikerental.coreapi.rental.ApproveRequestCommand;
import io.axoniq.demo.bikerental.coreapi.rental.BikeStatusNamedQueries;
import io.axoniq.demo.bikerental.coreapi.rental.CountOfBikesByTypeQuery;
import io.axoniq.demo.bikerental.coreapi.rental.RegisterBikeCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RejectRequestCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RequestBikeCommand;
import io.axoniq.demo.bikerental.coreapi.rental.ReturnBikeCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers this application with Axon Server's Integration option once it has started. This is the
 * HTTP equivalent of the gRPC branch's {@code registerCommandHandler}/{@code registerQueryHandler}
 * and {@code openStream} calls: it defines an endpoint (where Axon Server can reach us) and then
 * registers command, query and event handlers so Axon Server routes/pushes messages to the
 * controllers under {@code /axon/**}.
 * <p>
 * The bike read model is fed by one event handler (a persistent stream from {@code TAIL}, so it
 * processes the full history) and the payment saga by another (from {@code HEAD}, so it only reacts
 * to new bike requests - matching the gRPC branch).
 */
@Component
public class IntegrationRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationRegistrar.class);
    private static final int LOAD_FACTOR = 100;
    private static final int BATCH_SIZE = 100;

    static final String COMMAND_URL = "/axon/command";
    static final String QUERY_URL = "/axon/query";
    static final String BIKE_PROJECTION_URL = "/axon/events/bike-projection";
    static final String PAYMENT_SAGA_URL = "/axon/events/payment-saga";

    private final AxonServerClient client;
    private final String endpointName;
    private final String callbackUrl;

    public IntegrationRegistrar(AxonServerClient client,
                                @Value("${axon.integration.name:rental}") String endpointName,
                                @Value("${axon.integration.callback-url:http://localhost:8080}") String callbackUrl) {
        this.client = client;
        this.endpointName = endpointName;
        this.callbackUrl = callbackUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        try {
            String endpointId = client.registerEndpoint(endpoint());
            for (String command : List.of(RegisterBikeCommand.class.getName(),
                                          RequestBikeCommand.class.getName(),
                                          ApproveRequestCommand.class.getName(),
                                          RejectRequestCommand.class.getName(),
                                          ReturnBikeCommand.class.getName())) {
                client.registerCommandHandler(endpointId, commandHandler(command));
            }
            for (String query : List.of(BikeStatusNamedQueries.FIND_ALL,
                                        BikeStatusNamedQueries.FIND_ONE,
                                        BikeStatusNamedQueries.FIND_AVAILABLE,
                                        CountOfBikesByTypeQuery.class.getName())) {
                client.registerQueryHandler(endpointId, queryHandler(query));
            }
            client.registerEventHandler(endpointId,
                                        eventHandler("rental-bike-projection", BIKE_PROJECTION_URL, "TAIL"));
            client.registerEventHandler(endpointId,
                                        eventHandler("rental-payment-saga", PAYMENT_SAGA_URL, "HEAD"));
            logger.info("Registered integration endpoint '{}' at {}", endpointName, callbackUrl);
        } catch (Exception e) {
            logger.error("Failed to register integration endpoint with Axon Server. "
                                 + "Commands, queries and events will not be routed to this application.", e);
        }
    }

    private Map<String, Object> endpoint() {
        Map<String, Object> endpoint = new HashMap<>();
        endpoint.put("name", endpointName);
        endpoint.put("type", "HTTP");
        endpoint.put("wrappingType", "Wrapped");
        endpoint.put("baseUrl", callbackUrl);
        endpoint.put("healthUrl", "/axon/health");
        endpoint.put("commandUrl", COMMAND_URL);
        endpoint.put("queryUrl", QUERY_URL);
        endpoint.put("eventUrl", BIKE_PROJECTION_URL);
        return endpoint;
    }

    private Map<String, Object> commandHandler(String name) {
        Map<String, Object> handler = new HashMap<>();
        handler.put("name", name);
        handler.put("loadFactor", LOAD_FACTOR);
        handler.put("commandUrl", COMMAND_URL);
        return handler;
    }

    private Map<String, Object> queryHandler(String name) {
        Map<String, Object> handler = new HashMap<>();
        handler.put("name", name);
        handler.put("queryUrl", QUERY_URL);
        return handler;
    }

    private Map<String, Object> eventHandler(String name, String eventUrl, String startPosition) {
        Map<String, Object> handler = new HashMap<>();
        handler.put("name", name);
        handler.put("eventUrl", eventUrl);
        handler.put("startPosition", startPosition);
        handler.put("batchSize", BATCH_SIZE);
        handler.put("segments", 1);
        return handler;
    }
}
