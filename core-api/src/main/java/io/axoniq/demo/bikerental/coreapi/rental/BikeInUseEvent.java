package io.axoniq.demo.bikerental.coreapi.rental;

public record BikeInUseEvent(String bikeId, String renter) {
}

