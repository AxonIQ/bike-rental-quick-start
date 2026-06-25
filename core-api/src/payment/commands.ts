// The payment command messages.

export class PreparePaymentCommand {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.payment.PreparePaymentCommand';

  constructor(
    public readonly amount: number,
    public readonly paymentReference: string,
  ) {}
}

export class ConfirmPaymentCommand {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.payment.ConfirmPaymentCommand';

  constructor(public readonly paymentId: string) {}
}

export class RejectPaymentCommand {
  static readonly TYPE = 'io.axoniq.demo.bikerental.coreapi.payment.RejectPaymentCommand';

  constructor(public readonly paymentId: string) {}
}
