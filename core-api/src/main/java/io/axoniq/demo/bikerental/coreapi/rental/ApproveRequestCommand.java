package io.axoniq.demo.bikerental.coreapi.rental;

public record ApproveRequestCommand(String bikeId, String renter) {
}
