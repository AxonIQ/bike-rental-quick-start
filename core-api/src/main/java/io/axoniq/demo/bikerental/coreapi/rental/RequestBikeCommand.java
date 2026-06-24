package io.axoniq.demo.bikerental.coreapi.rental;

public record RequestBikeCommand(String bikeId, String renter) {
}
