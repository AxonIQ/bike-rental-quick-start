package io.axoniq.demo.bikerental.coreapi.rental;

public record ReturnBikeCommand(String bikeId, String location) {
}
