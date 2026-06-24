package io.axoniq.demo.bikerental.payment.support;

import io.axoniq.demo.bikerental.coreapi.payment.ConfirmPaymentCommand;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatusNamedQueries;
import io.axoniq.demo.bikerental.coreapi.payment.PreparePaymentCommand;
import io.axoniq.demo.bikerental.coreapi.payment.RejectPaymentCommand;
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
 * Registers the payment service with Axon Server's Integration option once it has started: an
 * endpoint where Axon Server can reach us, plus command, query and event handlers so Axon Server
 * routes/pushes messages to the controllers under {@code /axon/**}. The payment read model is fed by
 * one event handler (a persistent stream from {@code TAIL}).
 */
@Component
public class IntegrationRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationRegistrar.class);
    private static final int LOAD_FACTOR = 100;
    private static final int BATCH_SIZE = 100;

    static final String COMMAND_URL = "/axon/command";
    static final String QUERY_URL = "/axon/query";
    static final String PAYMENT_PROJECTION_URL = "/axon/events/payment-status";
    static final String GET_STATUS = "getStatus";

    private final AxonServerClient client;
    private final String endpointName;
    private final String callbackUrl;

    public IntegrationRegistrar(AxonServerClient client,
                                @Value("${axon.integration.name:payment}") String endpointName,
                                @Value("${axon.integration.callback-url:http://localhost:8081}") String callbackUrl) {
        this.client = client;
        this.endpointName = endpointName;
        this.callbackUrl = callbackUrl;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        try {
            String endpointId = client.registerEndpoint(endpoint());
            for (String command : List.of(PreparePaymentCommand.class.getName(),
                                          ConfirmPaymentCommand.class.getName(),
                                          RejectPaymentCommand.class.getName())) {
                client.registerCommandHandler(endpointId, commandHandler(command));
            }
            for (String query : List.of(GET_STATUS,
                                        PaymentStatusNamedQueries.GET_PAYMENT_ID,
                                        PaymentStatusNamedQueries.GET_ALL_PAYMENTS)) {
                client.registerQueryHandler(endpointId, queryHandler(query));
            }
            client.registerEventHandler(endpointId,
                                        eventHandler("payment-status-projection", PAYMENT_PROJECTION_URL, "TAIL"));
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
        endpoint.put("eventUrl", PAYMENT_PROJECTION_URL);
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
