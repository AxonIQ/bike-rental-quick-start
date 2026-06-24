package io.axoniq.demo.bikerental.payment.support;

/**
 * Thrown on the dispatching side when Axon Server reports that a command handler completed
 * exceptionally (the gRPC {@code CommandResponse} carried an error code).
 */
public class CommandExecutionException extends RuntimeException {

    private final String errorCode;

    public CommandExecutionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
