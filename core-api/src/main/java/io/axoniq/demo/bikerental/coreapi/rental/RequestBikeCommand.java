package io.axoniq.demo.bikerental.coreapi.rental;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record RequestBikeCommand(@TargetAggregateIdentifier String bikeId, String renter) {
}

