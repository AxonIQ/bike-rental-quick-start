// The payment domain events, stored in (and re-read from) Axon Server's event store.

export class PaymentPreparedEvent {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.payment.PaymentPreparedEvent';

  constructor(
    public readonly paymentId: string,
    public readonly amount: number,
    public readonly paymentReference: string,
  ) {}
}

export class PaymentConfirmedEvent {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.payment.PaymentConfirmedEvent';

  constructor(
    public readonly paymentId: string,
    public readonly paymentReference: string,
  ) {}
}

export class PaymentRejectedEvent {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.payment.PaymentRejectedEvent';

  constructor(
    public readonly paymentId: string,
    public readonly paymentReference: string,
  ) {}
}
