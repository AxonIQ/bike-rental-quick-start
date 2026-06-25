// The rental domain events, stored in (and re-read from) Axon Server's event store.

export class BikeRegisteredEvent {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.rental.BikeRegisteredEvent';

  constructor(
    public readonly bikeId: string,
    public readonly bikeType: string,
    public readonly location: string,
  ) {}
}

export class BikeRequestedEvent {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.rental.BikeRequestedEvent';

  constructor(
    public readonly bikeId: string,
    public readonly renter: string,
    public readonly rentalReference: string,
  ) {}
}

export class BikeInUseEvent {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.rental.BikeInUseEvent';

  constructor(
    public readonly bikeId: string,
    public readonly renter: string,
  ) {}
}

export class BikeReturnedEvent {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.rental.BikeReturnedEvent';

  constructor(
    public readonly bikeId: string,
    public readonly location: string,
  ) {}
}

export class RequestRejectedEvent {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.rental.RequestRejectedEvent';

  constructor(public readonly bikeId: string) {}
}
