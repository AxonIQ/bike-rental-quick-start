package io.axoniq.demo.bikerental.coreapi.rental;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record ReturnBikeCommand(@TargetAggregateIdentifier String bikeId, String location) {

}
