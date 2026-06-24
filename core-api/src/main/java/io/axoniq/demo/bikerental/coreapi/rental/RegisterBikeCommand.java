package io.axoniq.demo.bikerental.coreapi.rental;

public record RegisterBikeCommand(String bikeId,
                                  String bikeType,
                                  String location) {
}
