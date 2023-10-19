package io.axoniq.demo.bikerental.coreapi.payment;

import org.axonframework.commandhandling.RoutingKey;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record PreparePaymentCommand(int amount, @RoutingKey String paymentReference) {
}


