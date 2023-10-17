package io.axoniq.demo.bikerental.coreapi.rental;

public record BikeReturnedEvent(String bikeId, String location) {
}
