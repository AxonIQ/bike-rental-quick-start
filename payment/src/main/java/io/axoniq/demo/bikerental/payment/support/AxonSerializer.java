package io.axoniq.demo.bikerental.payment.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts application payloads (commands, events, queries, results) to and from the JSON
 * {@link JsonNode} representation used in the Axon Server Integration HTTP API message envelopes
 * (see {@link AxonServerMessages}). The fully qualified class name travels with the payload, so the
 * receiving side can reconstruct the original object.
 */
@Component
public class AxonSerializer {

    private final ObjectMapper objectMapper;

    public AxonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode toJson(Object payload) {
        return objectMapper.valueToTree(payload);
    }

    public <T> T fromJson(JsonNode payload, Class<T> type) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        return objectMapper.convertValue(payload, type);
    }

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
