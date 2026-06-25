import {
  ApproveRequestCommand,
  BikeRequestedEvent,
  CommandDispatcher,
  createLogger,
  PaymentConfirmedEvent,
  PaymentPreparedEvent,
  PaymentRejectedEvent,
  PreparePaymentCommand,
  RejectPaymentCommand,
  RejectRequestCommand,
  RequestRejectedEvent,
} from '@bikerental/core-api';

const logger = createLogger('PaymentSaga');

const PAYMENT_AMOUNT = 10;
const CANCEL_AFTER_MS = 30_000;
const RETRY_AFTER_MS = 5_000;

interface Instance {
  readonly bikeId: string;
  readonly renter: string;
  readonly paymentReference: string;
  paymentId?: string;
  cancelDeadline?: NodeJS.Timeout;
}

/**
 * Coordinates payment for a bike request. This used to be an Axon Framework `@Saga`; here it is an
 * in-memory state machine fed by an event-handler callback and using timers for the deadlines.
 *
 * Flow: a bike request kicks off a payment; once prepared we give the renter 30s to pay before the
 * payment is auto-rejected; a confirmed payment approves the request, a rejected payment rejects it.
 * Saga instances are keyed by the payment reference, with a secondary index on bike id.
 */
export class PaymentSaga {
  private readonly byReference = new Map<string, Instance>();
  private readonly referenceByBikeId = new Map<string, string>();

  constructor(private readonly commandDispatcher: CommandDispatcher) {}

  /** Entry point used by the event handler controller; events the saga ignores fall through. */
  accept(event: unknown): void {
    this.on(event);
  }

  on(event: unknown): void {
    if (event instanceof BikeRequestedEvent) {
      this.onBikeRequested(event);
    } else if (event instanceof PaymentPreparedEvent) {
      this.onPaymentPrepared(event);
    } else if (event instanceof PaymentConfirmedEvent) {
      this.onPaymentConfirmed(event);
    } else if (event instanceof PaymentRejectedEvent) {
      this.onPaymentRejected(event);
    } else if (event instanceof RequestRejectedEvent) {
      this.onRequestRejected(event);
    }
  }

  private onBikeRequested(event: BikeRequestedEvent): void {
    const instance: Instance = {
      bikeId: event.bikeId,
      renter: event.renter,
      paymentReference: event.rentalReference,
    };
    this.byReference.set(event.rentalReference, instance);
    this.referenceByBikeId.set(event.bikeId, event.rentalReference);
    this.preparePayment(instance);
  }

  private onPaymentPrepared(event: PaymentPreparedEvent): void {
    const instance = this.byReference.get(event.paymentReference);
    if (!instance) {
      return;
    }
    instance.paymentId = event.paymentId;
    instance.cancelDeadline = setTimeout(() => {
      void this.commandDispatcher.send(new RejectPaymentCommand(event.paymentId));
    }, CANCEL_AFTER_MS);
  }

  private onPaymentConfirmed(event: PaymentConfirmedEvent): void {
    const instance = this.byReference.get(event.paymentReference);
    if (!instance) {
      return;
    }
    void this.commandDispatcher.send(new ApproveRequestCommand(instance.bikeId, instance.renter));
    this.end(instance);
  }

  private onPaymentRejected(event: PaymentRejectedEvent): void {
    const instance = this.byReference.get(event.paymentReference);
    if (!instance) {
      return;
    }
    void this.commandDispatcher.send(new RejectRequestCommand(instance.bikeId, instance.renter));
  }

  private onRequestRejected(event: RequestRejectedEvent): void {
    const reference = this.referenceByBikeId.get(event.bikeId);
    if (!reference) {
      return;
    }
    const instance = this.byReference.get(reference);
    if (instance) {
      this.end(instance);
    }
  }

  private preparePayment(instance: Instance): void {
    this.commandDispatcher
      .send(new PreparePaymentCommand(PAYMENT_AMOUNT, instance.paymentReference))
      .catch(() => {
        if (this.byReference.has(instance.paymentReference)) {
          logger.warn(
            `Preparing payment for ${instance.paymentReference} failed, retrying in ` +
              `${RETRY_AFTER_MS / 1000}s`,
          );
          setTimeout(() => this.preparePayment(instance), RETRY_AFTER_MS);
        }
      });
  }

  private end(instance: Instance): void {
    if (instance.cancelDeadline) {
      clearTimeout(instance.cancelDeadline);
    }
    this.byReference.delete(instance.paymentReference);
    this.referenceByBikeId.delete(instance.bikeId);
  }
}
