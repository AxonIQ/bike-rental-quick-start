package io.axoniq.demo.bikerental.coreapi.payment;

public record PaymentConfirmedEvent(String paymentId, String paymentReference) {
}
