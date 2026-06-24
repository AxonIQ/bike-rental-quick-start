package io.axoniq.demo.bikerental.rental.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts application payloads (commands, events, queries, results - all plain Java records/objects)
 * to and from the JSON {@link JsonNode} representation used in the Axon Server Integration HTTP API
 * message envelopes (see {@link AxonServerMessages}).
 * <p>
 * The convention is the one Axon Framework's Jackson serializer uses: the message carries the fully
 * qualified class name as its {@code payloadType}, and the {@code payload} is the JSON form of the
 * object. Because the type travels with the payload, the receiving side can reconstruct the original.
 */
@Component
public class AxonSerializer {

    private final ObjectMapper objectMapper;

    public AxonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** The JSON form of a payload object, ready to drop into a message envelope. */
    public JsonNode toJson(Object payload) {
        return objectMapper.valueToTree(payload);
    }

    public <T> T fromJson(JsonNode payload, Class<T> type) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        return objectMapper.convertValue(payload, type);
    }

    /** Reconstructs an object from its JSON payload using the fully qualified type carried with it. */
    public Object fromJson(JsonNode payload, String payloadType) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        try {
            return fromJson(payload, Class.forName(payloadType));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unknown payload type: " + payloadType, e);
        }
    }

    public <T> List<T> listFromJson(JsonNode payload, Class<T> elementType) {
        if (payload == null || payload.isNull()) {
            return List.of();
        }
        return objectMapper.convertValue(
                payload,
                objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }
}
