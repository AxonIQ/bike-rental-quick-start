package io.axoniq.demo.bikerental.coreapi.payment;

public record PaymentPreparedEvent(String paymentId, int amount, String paymentReference) {
}
