package io.axoniq.demo.bikerental.rental.support;

/**
 * Thrown on the dispatching side when Axon Server reports that a query handler completed
 * exceptionally (the gRPC {@code QueryResponse} carried an error code).
 */
public class QueryExecutionException extends RuntimeException {

    public QueryExecutionException(String message) {
        super(message);
    }
}
