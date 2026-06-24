package io.axoniq.demo.bikerental.coreapi.rental;

public record RejectRequestCommand(String bikeId, String renter) {
}
