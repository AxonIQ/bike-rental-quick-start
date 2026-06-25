import { randomUUID } from 'node:crypto';
import {
  AxonSerializer,
  AxonServerClient,
  ConfirmPaymentCommand,
  eventType,
  KeyedMutex,
  PaymentPreparedEvent,
  payloadTypeName,
  PreparePaymentCommand,
  RejectPaymentCommand,
  type Command,
  type Event,
  type Json,
} from '@bikerental/core-api';
import { Payment } from './payment.js';

const AGGREGATE_TYPE = 'Payment';

/**
 * Command handling for the Payment "aggregate", implemented against the Axon Server Integration HTTP
 * API and invoked by the command handler controller. {@link PreparePaymentCommand} creates a new
 * payment (a fresh aggregate id + first event); confirm/reject load the payment by replaying its
 * events, validate, and append a new event.
 */
export class PaymentCommandHandler {
  private readonly locks = new KeyedMutex();

  constructor(
    private readonly client: AxonServerClient,
    private readonly serializer: AxonSerializer,
  ) {}

  async handle(command: Command): Promise<Json | null> {
    const payload = this.serializer.fromJson<object>(command.payload, commandType(command));
    const result = await this.dispatch(payload);
    return result === null || result === undefined ? null : this.serializer.toJson(result);
  }

  private async dispatch(payload: unknown): Promise<unknown> {
    if (payload instanceof PreparePaymentCommand) {
      const paymentId = randomUUID();
      // A new payment: no history to load, just append the first event under a fresh id.
      await this.append(paymentId, -1, [
        new PaymentPreparedEvent(paymentId, payload.amount, payload.paymentReference),
      ]);
      return paymentId;
    }
    if (payload instanceof ConfirmPaymentCommand) {
      await this.applyToPayment(payload.paymentId, (payment) => payment.decideOnConfirm(payload));
      return null;
    }
    if (payload instanceof RejectPaymentCommand) {
      await this.applyToPayment(payload.paymentId, (payment) => payment.decideOnReject(payload));
      return null;
    }
    throw new Error(`Unsupported command: ${(payload as object)?.constructor?.name}`);
  }

  private applyToPayment(paymentId: string, decision: (payment: Payment) => object[]): Promise<void> {
    return this.locks.run(paymentId, async () => {
      const payment = await this.load(paymentId);
      const events = decision(payment);
      await this.append(paymentId, payment.lastSequence(), events);
    });
  }

  private async load(paymentId: string): Promise<Payment> {
    const payment = new Payment();
    for (const event of await this.client.readAggregateEvents(paymentId)) {
      payment.setLastSequence(event.sequenceNumber ?? -1);
      payment.apply(this.serializer.fromJson(event.payload, eventType(event)));
    }
    return payment;
  }

  private async append(paymentId: string, lastSequence: number, events: object[]): Promise<void> {
    if (events.length === 0) {
      return;
    }
    let sequence = lastSequence;
    const messages: Event[] = events.map((payload) => {
      sequence++;
      const type = payloadTypeName(payload)!;
      return {
        name: type,
        payloadType: type,
        id: randomUUID(),
        aggregateId: paymentId,
        aggregateType: AGGREGATE_TYPE,
        sequenceNumber: sequence,
        payload: this.serializer.toJson(payload),
      };
    });
    await this.client.appendEvents(messages);
  }
}

function commandType(command: Command): string {
  return command.payloadType ?? command.name ?? '';
}
