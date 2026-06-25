import {
  ConfirmPaymentCommand,
  PaymentConfirmedEvent,
  PaymentPreparedEvent,
  PaymentRejectedEvent,
  RejectPaymentCommand,
} from '@bikerental/core-api';

/**
 * The Payment decision model. Formerly an Axon Framework `@Aggregate`; now a plain object that
 * {@link PaymentCommandHandler} rebuilds by replaying events (via {@link apply}) before asking it to
 * decide on a command.
 */
export class Payment {
  private closed = false;
  private paymentReference?: string;
  private lastSequenceNumber = -1;

  apply(event: unknown): void {
    if (event instanceof PaymentPreparedEvent) {
      this.paymentReference = event.paymentReference;
    } else if (event instanceof PaymentConfirmedEvent) {
      this.closed = true;
    } else if (event instanceof PaymentRejectedEvent) {
      this.closed = true;
    }
  }

  decideOnConfirm(command: ConfirmPaymentCommand): object[] {
    if (this.closed) {
      return [];
    }
    return [new PaymentConfirmedEvent(command.paymentId, this.paymentReference!)];
  }

  decideOnReject(command: RejectPaymentCommand): object[] {
    if (this.closed) {
      return [];
    }
    return [new PaymentRejectedEvent(command.paymentId, this.paymentReference!)];
  }

  lastSequence(): number {
    return this.lastSequenceNumber;
  }

  setLastSequence(lastSequence: number): void {
    this.lastSequenceNumber = lastSequence;
  }
}
