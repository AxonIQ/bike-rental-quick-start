package io.axoniq.demo.bikerental.rental.support;

import io.axoniq.axonserver.connector.command.CommandChannel;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Thin replacement for Axon Framework's {@code CommandGateway}, built straight on top of the
 * Axon Server {@link CommandChannel}. A command is sent as a gRPC {@link Command} whose
 * {@code name} is the payload's fully qualified class name (that is how the handler subscribes)
 * and whose payload is the JSON serialized command object.
 */
@Component
public class CommandDispatcher {

    private final CommandChannel commandChannel;
    private final AxonSerializer serializer;

    public CommandDispatcher(CommandChannel commandChannel, AxonSerializer serializer) {
        this.commandChannel = commandChannel;
        this.serializer = serializer;
    }

    public CompletableFuture<Void> send(Object payload) {
        return send(payload, Void.class);
    }

    public <R> CompletableFuture<R> send(Object payload, Class<R> responseType) {
        Command command = Command.newBuilder()
                                 .setName(payload.getClass().getName())
                                 .setPayload(serializer.serialize(payload))
                                 .build();
        return commandChannel.sendCommand(command)
                             .thenApply(response -> handleResponse(response, responseType));
    }

    private <R> R handleResponse(CommandResponse response, Class<R> responseType) {
        if (!response.getErrorCode().isEmpty()) {
            throw new CommandExecutionException(response.getErrorCode(),
                                                response.getErrorMessage().getMessage());
        }
        if (responseType == Void.class || !response.hasPayload()) {
            return null;
        }
        return serializer.deserialize(response.getPayload(), responseType);
    }
}
