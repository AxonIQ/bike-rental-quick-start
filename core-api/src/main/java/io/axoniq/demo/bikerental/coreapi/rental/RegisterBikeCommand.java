package io.axoniq.demo.bikerental.coreapi.rental;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record RegisterBikeCommand(@TargetAggregateIdentifier String bikeId,
                                  String bikeType,
                                  String location) {
}

