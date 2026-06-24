package io.axoniq.demo.bikerental.coreapi.payment;

public record PreparePaymentCommand(int amount, String paymentReference) {
}
