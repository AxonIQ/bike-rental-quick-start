package io.axoniq.demo.bikerental.rental.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.SerializedObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Converts application payloads (commands, events, queries, results - all plain Java records/objects)
 * to and from the {@link SerializedObject} representation that the Axon Server gRPC API works with.
 * <p>
 * The convention used here is the same one Axon Framework's Jackson serializer uses: the
 * {@code type} of the {@link SerializedObject} holds the fully qualified class name and the
 * {@code data} holds the JSON representation. Because the type travels with the payload, the
 * receiving side can always reconstruct the original object.
 */
@Component
public class AxonSerializer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SerializedObject serialize(Object payload) {
        try {
            return SerializedObject.newBuilder()
                                   .setType(payload.getClass().getName())
                                   .setData(ByteString.copyFrom(objectMapper.writeValueAsBytes(payload)))
                                   .build();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize " + payload.getClass(), e);
        }
    }

    public <T> T deserialize(SerializedObject serializedObject, Class<T> type) {
        try {
            return objectMapper.readValue(serializedObject.getData().toByteArray(), type);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize payload of type " + serializedObject.getType(), e);
        }
    }

    public Object deserialize(SerializedObject serializedObject) {
        try {
            return deserialize(serializedObject, Class.forName(serializedObject.getType()));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unknown payload type: " + serializedObject.getType(), e);
        }
    }

    public <T> List<T> deserializeList(SerializedObject serializedObject, Class<T> elementType) {
        try {
            return objectMapper.readValue(
                    serializedObject.getData().toByteArray(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize list payload", e);
        }
    }
}
