// The rental command messages. Mirroring the gRPC/Framework branches, each message carries — as its
// `TYPE` — the fully qualified Java class name it had in those branches, so the wire contract and the
// cross-service event/command contract are unchanged.

export class RegisterBikeCommand {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.rental.RegisterBikeCommand';

  constructor(
    public readonly bikeId: string,
    public readonly bikeType: string,
    public readonly location: string,
  ) {}
}

export class RequestBikeCommand {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.rental.RequestBikeCommand';

  constructor(
    public readonly bikeId: string,
    public readonly renter: string,
  ) {}
}

export class ApproveRequestCommand {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.rental.ApproveRequestCommand';

  constructor(
    public readonly bikeId: string,
    public readonly renter: string,
  ) {}
}

export class RejectRequestCommand {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.rental.RejectRequestCommand';

  constructor(
    public readonly bikeId: string,
    public readonly renter: string,
  ) {}
}

export class ReturnBikeCommand {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.rental.ReturnBikeCommand';

  constructor(
    public readonly bikeId: string,
    public readonly location: string,
  ) {}
}
