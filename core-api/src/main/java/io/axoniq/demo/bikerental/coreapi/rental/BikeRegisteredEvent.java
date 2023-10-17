package io.axoniq.demo.bikerental.coreapi.rental;

public record BikeRegisteredEvent(String bikeId, String bikeType, String location) {
}

