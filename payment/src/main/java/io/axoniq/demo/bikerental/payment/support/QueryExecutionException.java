package io.axoniq.demo.bikerental.payment.support;

/**
 * Thrown on the dispatching side when Axon Server reports that a query handler completed
 * exceptionally (the {@code POST /v2/queries} call returned an error status).
 */
public class QueryExecutionException extends RuntimeException {

    public QueryExecutionException(String message) {
        super(message);
    }
}
