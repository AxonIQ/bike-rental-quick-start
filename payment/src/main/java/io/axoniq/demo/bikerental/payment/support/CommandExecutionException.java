package io.axoniq.demo.bikerental.payment.support;

/**
 * Thrown on the dispatching side when Axon Server reports that a command handler completed
 * exceptionally (the {@code POST /v2/commands} call returned an error status).
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
