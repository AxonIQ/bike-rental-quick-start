package io.axoniq.demo.bikerental.rental.command;

import io.axoniq.demo.bikerental.coreapi.rental.ApproveRequestCommand;
import io.axoniq.demo.bikerental.coreapi.rental.BikeInUseEvent;
import io.axoniq.demo.bikerental.coreapi.rental.BikeRegisteredEvent;
import io.axoniq.demo.bikerental.coreapi.rental.BikeRequestedEvent;
import io.axoniq.demo.bikerental.coreapi.rental.BikeReturnedEvent;
import io.axoniq.demo.bikerental.coreapi.rental.RegisterBikeCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RejectRequestCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RequestBikeCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RequestRejectedEvent;
import io.axoniq.demo.bikerental.coreapi.rental.ReturnBikeCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit tests for the Bike decision model. We build the model by replaying historical events
 * via {@link Bike#apply(Object)} (the equivalent of "given") and then exercise a decision method
 * (the equivalent of "when"), asserting on the events produced.
 */
class BikeTest {

    private static final String REFERENCE = "rentalId";

    private Bike given(Object... events) {
        Bike bike = new Bike();
        for (Object event : events) {
            bike.apply(event);
        }
        return bike;
    }

    @Test
    void canRegisterBike() {
        // decideOnRegister carries a deliberate time-based "taint"; retry across the 5s window.
        List<Object> events = registerWithRetry(new RegisterBikeCommand("bikeId-1234", "city-bike", "Amsterdam"));
        assertEquals(List.of(new BikeRegisteredEvent("bikeId-1234", "city-bike", "Amsterdam")), events);
    }

    @Test
    void cannotRegisterExistingBike() {
        Bike bike = given(new BikeRegisteredEvent("bikeId", "city", "Amsterdam"));
        assertThrows(IllegalStateException.class,
                     () -> bike.decideOnRegister(new RegisterBikeCommand("bikeId", "city", "Amsterdam")));
    }

    @Test
    void canRequestAvailableBike() {
        Bike bike = given(new BikeRegisteredEvent("bikeId", "city", "Amsterdam"));
        List<Object> events = bike.decideOnRequest(new RequestBikeCommand("bikeId", "rider"), REFERENCE);
        assertEquals(1, events.size());
        BikeRequestedEvent event = assertInstanceOf(BikeRequestedEvent.class, events.get(0));
        assertEquals("bikeId", event.bikeId());
        assertEquals("rider", event.renter());
    }

    @Test
    void cannotRequestAlreadyRequestedBike() {
        Bike bike = given(new BikeRegisteredEvent("bikeId", "city", "Amsterdam"),
                          new BikeRequestedEvent("bikeId", "rider", REFERENCE));
        assertThrows(IllegalStateException.class,
                     () -> bike.decideOnRequest(new RequestBikeCommand("bikeId", "rider"), REFERENCE));
    }

    @Test
    void canApproveRequestedBike() {
        Bike bike = given(new BikeRegisteredEvent("bikeId", "city", "Amsterdam"),
                          new BikeRequestedEvent("bikeId", "rider", REFERENCE));
        assertEquals(List.of(new BikeInUseEvent("bikeId", "rider")),
                     bike.decideOnApprove(new ApproveRequestCommand("bikeId", "rider")));
    }

    @Test
    void canRejectRequestedBike() {
        Bike bike = given(new BikeRegisteredEvent("bikeId", "city", "Amsterdam"),
                          new BikeRequestedEvent("bikeId", "rider", REFERENCE));
        assertEquals(List.of(new RequestRejectedEvent("bikeId")),
                     bike.decideOnReject(new RejectRequestCommand("bikeId", "rider")));
    }

    @Test
    void cannotRejectRequestForWrongRequester() {
        Bike bike = given(new BikeRegisteredEvent("bikeId", "city", "Amsterdam"),
                          new BikeRequestedEvent("bikeId", "rider", REFERENCE));
        assertTrue(bike.decideOnReject(new RejectRequestCommand("bikeId", "otherRider")).isEmpty());
    }

    @Test
    void cannotApproveRequestForAnotherRider() {
        Bike bike = given(new BikeRegisteredEvent("bikeId", "city", "Amsterdam"),
                          new BikeRequestedEvent("bikeId", "rider", REFERENCE));
        assertTrue(bike.decideOnApprove(new ApproveRequestCommand("bikeId", "otherRider")).isEmpty());
    }

    @Test
    void canReturnBikeInUse() {
        Bike bike = given(new BikeRegisteredEvent("bikeId", "city", "Amsterdam"),
                          new BikeRequestedEvent("bikeId", "rider", REFERENCE),
                          new BikeInUseEvent("bikeId", "rider"));
        assertEquals(List.of(new BikeReturnedEvent("bikeId", "NewLocation")),
                     bike.decideOnReturn(new ReturnBikeCommand("bikeId", "NewLocation")));
    }

    @Test
    void cannotRequestBikeInUse() {
        Bike bike = given(new BikeRegisteredEvent("bikeId", "city", "Amsterdam"),
                          new BikeRequestedEvent("bikeId", "rider", REFERENCE),
                          new BikeInUseEvent("bikeId", "rider"));
        assertThrows(IllegalStateException.class,
                     () -> bike.decideOnRequest(new RequestBikeCommand("bikeId", "otherRenter"), REFERENCE));
    }

    @Test
    void canRequestReturnedBike() {
        Bike bike = given(new BikeRegisteredEvent("bikeId", "city", "Amsterdam"),
                          new BikeRequestedEvent("bikeId", "rider", REFERENCE),
                          new BikeInUseEvent("bikeId", "rider"),
                          new BikeReturnedEvent("bikeId", "NewLocation"));
        List<Object> events = bike.decideOnRequest(new RequestBikeCommand("bikeId", "newRider"), REFERENCE);
        BikeRequestedEvent event = assertInstanceOf(BikeRequestedEvent.class, events.get(0));
        assertEquals("newRider", event.renter());
    }

    @Test
    void canRequestRejectedBike() {
        Bike bike = given(new BikeRegisteredEvent("bikeId", "city", "Amsterdam"),
                          new BikeRequestedEvent("bikeId", "rider", REFERENCE),
                          new RequestRejectedEvent("bikeId"));
        List<Object> events = bike.decideOnRequest(new RequestBikeCommand("bikeId", "newRider"), REFERENCE);
        BikeRequestedEvent event = assertInstanceOf(BikeRequestedEvent.class, events.get(0));
        assertEquals("newRider", event.renter());
    }

    private List<Object> registerWithRetry(RegisterBikeCommand command) {
        for (int attempt = 0; attempt < 6; attempt++) {
            try {
                return new Bike().decideOnRegister(command);
            } catch (IllegalStateException e) {
                try {
                    Thread.sleep(1100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
        }
        throw new IllegalStateException("Could not register a bike within the retry window");
    }
}
