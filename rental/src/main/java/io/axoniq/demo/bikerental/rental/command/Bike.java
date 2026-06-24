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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The Bike decision model. This used to be an Axon Framework {@code @Aggregate}; now it is a plain
 * object. {@link BikeCommandHandler} rebuilds it by replaying the bike's events (via
 * {@link #apply(Object)}) and then asks it to decide on a command (the {@code decideOn...} methods),
 * which return the new events to append - without any framework involved.
 */
public class Bike {

    private String bikeId;
    private boolean available;
    private String reservedBy;
    private boolean reservationConfirmed;

    private boolean exists;
    private long lastSequence = -1;

    /** Replays a single historical event onto this model. */
    public void apply(Object event) {
        if (event instanceof BikeRegisteredEvent e) {
            this.bikeId = e.bikeId();
            this.available = true;
            this.exists = true;
        } else if (event instanceof BikeRequestedEvent e) {
            this.reservedBy = e.renter();
            this.reservationConfirmed = false;
            this.available = false;
        } else if (event instanceof BikeInUseEvent e) {
            this.available = false;
            this.reservationConfirmed = true;
        } else if (event instanceof BikeReturnedEvent e) {
            this.available = true;
            this.reservationConfirmed = false;
            this.reservedBy = null;
        } else if (event instanceof RequestRejectedEvent e) {
            this.reservedBy = null;
            this.reservationConfirmed = false;
            this.available = true;
        }
    }

    public List<Object> decideOnRegister(RegisterBikeCommand command) {
        if (exists) {
            throw new IllegalStateException("Bike already exists");
        }
        var seconds = Instant.now().getEpochSecond();
        if (seconds % 5 == 0) {
            throw new IllegalStateException("Can't accept new bikes right now");
        }
        return List.of(new BikeRegisteredEvent(command.bikeId(), command.bikeType(), command.location()));
    }

    public List<Object> decideOnRequest(RequestBikeCommand command, String rentalReference) {
        if (!available) {
            throw new IllegalStateException("Bike is already rented");
        }
        return List.of(new BikeRequestedEvent(command.bikeId(), command.renter(), rentalReference));
    }

    public List<Object> decideOnApprove(ApproveRequestCommand command) {
        if (!Objects.equals(reservedBy, command.renter()) || reservationConfirmed) {
            return List.of();
        }
        return List.of(new BikeInUseEvent(command.bikeId(), command.renter()));
    }

    public List<Object> decideOnReject(RejectRequestCommand command) {
        if (!Objects.equals(reservedBy, command.renter()) || reservationConfirmed) {
            return List.of();
        }
        return List.of(new RequestRejectedEvent(command.bikeId()));
    }

    public List<Object> decideOnReturn(ReturnBikeCommand command) {
        if (available) {
            throw new IllegalStateException("Bike was already returned");
        }
        return List.of(new BikeReturnedEvent(command.bikeId(), command.location()));
    }

    public boolean exists() {
        return exists;
    }

    public long lastSequence() {
        return lastSequence;
    }

    public void setLastSequence(long lastSequence) {
        this.lastSequence = lastSequence;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getReservedBy() {
        return reservedBy;
    }

    public boolean isReservationConfirmed() {
        return reservationConfirmed;
    }
}
