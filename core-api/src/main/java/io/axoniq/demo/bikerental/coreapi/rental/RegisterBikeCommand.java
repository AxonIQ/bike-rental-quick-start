package io.axoniq.demo.bikerental.coreapi.rental;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record RegisterBikeCommand(@TargetAggregateIdentifier String bikeId, // <1>
                                  String bikeType,
                                  String location) {
}

