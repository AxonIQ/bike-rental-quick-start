import {
  AxonSerializer,
  JavaTypes,
  PaymentConfirmedEvent,
  PaymentPreparedEvent,
  PaymentRejectedEvent,
  PaymentStatus,
  PaymentStatusNamedQueries,
  PaymentStatusValue,
  type Query,
} from '@bikerental/core-api';
import { PaymentStatusRepository } from './payment-status-repository.js';

const GET_STATUS = 'getStatus';

/**
 * The payment read model. It answers point-to-point queries (invoked by the query handler controller
 * when Axon Server routes a query here) and is fed events by the event handler controller. The
 * `getPaymentId` query lets a renter find the payment id once a payment for its reference has been
 * prepared.
 */
export class PaymentStatusProjection {
  constructor(
    private readonly repository: PaymentStatusRepository,
    private readonly serializer: AxonSerializer,
  ) {}

  // -------------------------------------------------------------------------------------------
  // Query handling
  // -------------------------------------------------------------------------------------------

  handle(query: Query): unknown {
    switch (query.name) {
      case GET_STATUS:
        return this.repository.findById(this.payloadAsString(query)) ?? null;
      case PaymentStatusNamedQueries.GET_PAYMENT_ID:
        return this.getPaymentId(query);
      case PaymentStatusNamedQueries.GET_ALL_PAYMENTS:
        return this.findByStatus(query);
      default:
        throw new Error(`No handler for query ${query.name}`);
    }
  }

  private getPaymentId(query: Query): string | null {
    return (
      this.repository
        .findByReferenceAndStatus(this.payloadAsString(query), PaymentStatusValue.PENDING)
        ?.getId() ?? null
    );
  }

  private findByStatus(query: Query): PaymentStatus[] {
    const status = query.payload == null ? null : (query.payload as PaymentStatusValue);
    return status === null ? this.repository.findAll() : this.repository.findAllByStatus(status);
  }

  // -------------------------------------------------------------------------------------------
  // Event handling (called by the event handler controller)
  // -------------------------------------------------------------------------------------------

  on(event: unknown): void {
    if (event instanceof PaymentPreparedEvent) {
      this.repository.save(new PaymentStatus(event.paymentId, event.amount, event.paymentReference));
    } else if (event instanceof PaymentConfirmedEvent) {
      this.updateStatus(event.paymentId, PaymentStatusValue.APPROVED);
    } else if (event instanceof PaymentRejectedEvent) {
      this.updateStatus(event.paymentId, PaymentStatusValue.REJECTED);
    }
  }

  private updateStatus(paymentId: string, status: PaymentStatusValue): void {
    const existing = this.repository.findById(paymentId);
    if (existing) {
      existing.setStatus(status);
      this.repository.save(existing);
    }
  }

  private payloadAsString(query: Query): string {
    return this.serializer.fromJson<string>(query.payload, JavaTypes.STRING)!;
  }
}
