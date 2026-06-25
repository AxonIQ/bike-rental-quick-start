import {
  ApproveRequestCommand,
  BikeInUseEvent,
  BikeRegisteredEvent,
  BikeRequestedEvent,
  BikeReturnedEvent,
  RegisterBikeCommand,
  RejectRequestCommand,
  RequestBikeCommand,
  RequestRejectedEvent,
  ReturnBikeCommand,
} from '@bikerental/core-api';

/**
 * The Bike decision model. This used to be an Axon Framework `@Aggregate`; now it is a plain object.
 * {@link BikeCommandHandler} rebuilds it by replaying the bike's events (via {@link apply}) and then
 * asks it to decide on a command (the `decideOn...` methods), which return the new events to append.
 */
export class Bike {
  private available = false;
  private reservedBy?: string;
  private reservationConfirmed = false;

  private exists = false;
  private lastSequenceNumber = -1;

  /** Replays a single historical event onto this model. */
  apply(event: unknown): void {
    if (event instanceof BikeRegisteredEvent) {
      this.available = true;
      this.exists = true;
    } else if (event instanceof BikeRequestedEvent) {
      this.reservedBy = event.renter;
      this.reservationConfirmed = false;
      this.available = false;
    } else if (event instanceof BikeInUseEvent) {
      this.available = false;
      this.reservationConfirmed = true;
    } else if (event instanceof BikeReturnedEvent) {
      this.available = true;
      this.reservationConfirmed = false;
      this.reservedBy = undefined;
    } else if (event instanceof RequestRejectedEvent) {
      this.reservedBy = undefined;
      this.reservationConfirmed = false;
      this.available = true;
    }
  }

  decideOnRegister(command: RegisterBikeCommand): object[] {
    if (this.exists) {
      throw new Error('Bike already exists');
    }
    const seconds = Math.floor(Date.now() / 1000);
    if (seconds % 5 === 0) {
      throw new Error("Can't accept new bikes right now");
    }
    return [new BikeRegisteredEvent(command.bikeId, command.bikeType, command.location)];
  }

  decideOnRequest(command: RequestBikeCommand, rentalReference: string): object[] {
    if (!this.available) {
      throw new Error('Bike is already rented');
    }
    return [new BikeRequestedEvent(command.bikeId, command.renter, rentalReference)];
  }

  decideOnApprove(command: ApproveRequestCommand): object[] {
    if (this.reservedBy !== command.renter || this.reservationConfirmed) {
      return [];
    }
    return [new BikeInUseEvent(command.bikeId, command.renter)];
  }

  decideOnReject(command: RejectRequestCommand): object[] {
    if (this.reservedBy !== command.renter || this.reservationConfirmed) {
      return [];
    }
    return [new RequestRejectedEvent(command.bikeId)];
  }

  decideOnReturn(command: ReturnBikeCommand): object[] {
    if (this.available) {
      throw new Error('Bike was already returned');
    }
    return [new BikeReturnedEvent(command.bikeId, command.location)];
  }

  lastSequence(): number {
    return this.lastSequenceNumber;
  }

  setLastSequence(lastSequence: number): void {
    this.lastSequenceNumber = lastSequence;
  }
}
