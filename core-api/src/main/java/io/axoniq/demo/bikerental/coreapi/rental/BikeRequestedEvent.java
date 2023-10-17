package io.axoniq.demo.bikerental.coreapi.rental;

public record BikeRequestedEvent(String bikeId, String renter, String rentalReference) {
}

