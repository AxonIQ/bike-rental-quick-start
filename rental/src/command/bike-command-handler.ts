import { randomUUID } from 'node:crypto';
import {
  ApproveRequestCommand,
  AxonSerializer,
  AxonServerClient,
  eventType,
  KeyedMutex,
  payloadTypeName,
  RegisterBikeCommand,
  RejectRequestCommand,
  RequestBikeCommand,
  ReturnBikeCommand,
  type Command,
  type Event,
  type Json,
} from '@bikerental/core-api';
import { Bike } from './bike.js';

const AGGREGATE_TYPE = 'Bike';

/**
 * Command handling for the Bike "aggregate", implemented against the Axon Server Integration HTTP API
 * and invoked by the command handler controller when Axon Server routes a command here. For every
 * command we load the bike by reading its event stream, let the {@link Bike} decision model validate
 * the command and produce new events, then append those events with the next sequence numbers. A
 * per-bike lock serializes commands for the same bike so sequence numbers never collide.
 */
export class BikeCommandHandler {
  private readonly locks = new KeyedMutex();

  constructor(
    private readonly client: AxonServerClient,
    private readonly serializer: AxonSerializer,
  ) {}

  /** Handles one routed command, returning the (optional) JSON result payload. */
  async handle(command: Command): Promise<Json | null> {
    const payload = this.serializer.fromJson<object>(command.payload, commandType(command));
    const result = await this.dispatch(payload);
    return result === null || result === undefined ? null : this.serializer.toJson(result);
  }

  private dispatch(payload: unknown): Promise<unknown> {
    if (payload instanceof RegisterBikeCommand) {
      return this.applyToBike(payload.bikeId, (bike) => bike.decideOnRegister(payload)).then(
        () => null,
      );
    }
    if (payload instanceof RequestBikeCommand) {
      const rentalReference = randomUUID();
      return this.applyToBike(payload.bikeId, (bike) =>
        bike.decideOnRequest(payload, rentalReference),
      ).then(() => rentalReference);
    }
    if (payload instanceof ApproveRequestCommand) {
      return this.applyToBike(payload.bikeId, (bike) => bike.decideOnApprove(payload)).then(
        () => null,
      );
    }
    if (payload instanceof RejectRequestCommand) {
      return this.applyToBike(payload.bikeId, (bike) => bike.decideOnReject(payload)).then(
        () => null,
      );
    }
    if (payload instanceof ReturnBikeCommand) {
      return this.applyToBike(payload.bikeId, (bike) => bike.decideOnReturn(payload)).then(
        () => null,
      );
    }
    throw new Error(`Unsupported command: ${(payload as object)?.constructor?.name}`);
  }

  private applyToBike(bikeId: string, decision: (bike: Bike) => object[]): Promise<void> {
    return this.locks.run(bikeId, async () => {
      const bike = await this.load(bikeId);
      const events = decision(bike);
      await this.append(bikeId, bike.lastSequence(), events);
    });
  }

  private async load(bikeId: string): Promise<Bike> {
    const bike = new Bike();
    for (const event of await this.client.readAggregateEvents(bikeId)) {
      bike.setLastSequence(event.sequenceNumber ?? -1);
      bike.apply(this.serializer.fromJson(event.payload, eventType(event)));
    }
    return bike;
  }

  private async append(bikeId: string, lastSequence: number, events: object[]): Promise<void> {
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
        aggregateId: bikeId,
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
