package io.axoniq.demo.bikerental.coreapi.payment;

public record PaymentRejectedEvent(String paymentId, String paymentReference) {
}
