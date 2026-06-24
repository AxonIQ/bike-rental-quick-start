package io.axoniq.demo.bikerental.rental.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * The JSON message envelopes of the Axon Server <em>Integration</em> HTTP API
 * (<a href="https://docs.axoniq.io/axon-server-reference/v2026.0/axon-server/administration/integration/">docs</a>).
 * These are the "Wrapped" representations: a command/query/event together with the routing and type
 * information Axon Server needs. They are used both when we <em>send</em> messages to Axon Server
 * (POST {@code /v2/commands|queries|events}) and when Axon Server <em>pushes</em> messages back to
 * the handler endpoints we register (see {@code *HandlerController}).
 * <p>
 * The convention - mirroring the gRPC branch - is that {@code payloadType}/{@code name} carry the
 * fully qualified class name and {@code payload} carries the JSON form of the object, so the
 * receiving side can always reconstruct the original.
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

    /**
     * An event message. We populate both {@code name} and {@code payloadType} with the event's
     * class name, since the human docs example uses {@code name} while the OpenAPI schema uses
     * {@code payloadType}; on the receiving side we accept whichever Axon Server provides.
     */
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
