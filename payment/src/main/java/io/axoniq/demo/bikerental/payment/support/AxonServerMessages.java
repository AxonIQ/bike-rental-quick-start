package io.axoniq.demo.bikerental.payment.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * The JSON message envelopes of the Axon Server <em>Integration</em> HTTP API (the "Wrapped" form),
 * used both when sending messages to Axon Server and when Axon Server pushes messages back to the
 * handler endpoints we register. {@code payloadType}/{@code name} carry the fully qualified class
 * name and {@code payload} the JSON form of the object, so the receiving side can reconstruct it.
 */
public final class AxonServerMessages {

    private AxonServerMessages() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Command(String name,
                          String payloadType,
                          String payloadRevision,
                          String routingKey,
                          Integer priority,
                          String id,
                          Map<String, Object> metaData,
                          JsonNode payload) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommandResult(String id,
                                String payloadType,
                                String payloadRevision,
                                Map<String, Object> metaData,
                                JsonNode payload) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Query(String name,
                        String payloadType,
                        String payloadRevision,
                        String responseType,
                        String responseCardinality,
                        Integer numberOfResponses,
                        String id,
                        Map<String, Object> metaData,
                        JsonNode payload) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QueryResult(String id,
                              String payloadType,
                              String payloadRevision,
                              Map<String, Object> metaData,
                              JsonNode payload) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(String name,
                        String payloadType,
                        String payloadRevision,
                        String id,
                        String aggregateId,
                        String aggregateType,
                        Long sequenceNumber,
                        Long index,
                        String dateTime,
                        Map<String, Object> metaData,
                        JsonNode payload) {

        /** The event type, tolerating both field spellings used across the API surface. */
        public String type() {
            return payloadType != null ? payloadType : name;
        }
    }
}
