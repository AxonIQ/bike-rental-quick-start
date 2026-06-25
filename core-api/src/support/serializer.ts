import { JavaTypes, type Json } from './messages.js';
import {
  ApproveRequestCommand,
  RegisterBikeCommand,
  RejectRequestCommand,
  RequestBikeCommand,
  ReturnBikeCommand,
} from '../rental/commands.js';
import {
  BikeInUseEvent,
  BikeRegisteredEvent,
  BikeRequestedEvent,
  BikeReturnedEvent,
  RequestRejectedEvent,
} from '../rental/events.js';
import { CountOfBikesByTypeQuery } from '../rental/queries.js';
import { BikeStatus } from '../rental/bike-status.js';
import {
  ConfirmPaymentCommand,
  PreparePaymentCommand,
  RejectPaymentCommand,
} from '../payment/commands.js';
import {
  PaymentConfirmedEvent,
  PaymentPreparedEvent,
  PaymentRejectedEvent,
} from '../payment/events.js';
import { PaymentStatus } from '../payment/payment-status.js';

/** A constructor that carries the (Java) type name its instances are identified by on the wire. */
interface Typed {
  readonly TYPE: string;
  readonly prototype: object;
}

// Every payload/entity that can be reconstructed from its JSON form by type name. This is the
// TypeScript stand-in for `Class.forName(payloadType)` in the Java serializer.
const REGISTERED: Typed[] = [
  RegisterBikeCommand,
  RequestBikeCommand,
  ApproveRequestCommand,
  RejectRequestCommand,
  ReturnBikeCommand,
  BikeRegisteredEvent,
  BikeRequestedEvent,
  BikeInUseEvent,
  BikeReturnedEvent,
  RequestRejectedEvent,
  CountOfBikesByTypeQuery,
  BikeStatus,
  PreparePaymentCommand,
  ConfirmPaymentCommand,
  RejectPaymentCommand,
  PaymentPreparedEvent,
  PaymentConfirmedEvent,
  PaymentRejectedEvent,
  PaymentStatus,
];

const BY_TYPE = new Map<string, Typed>(REGISTERED.map((c) => [c.TYPE, c]));

/**
 * The type name to put on a message for a given payload, mirroring Java's
 * {@code payload.getClass().getName()}: a domain object's {@code TYPE}, or the matching
 * {@code java.lang.*} name for a scalar.
 */
export function payloadTypeName(payload: unknown): string | undefined {
  if (payload === null || payload === undefined) {
    return undefined;
  }
  switch (typeof payload) {
    case 'object': {
      const type = (payload.constructor as Partial<Typed> | undefined)?.TYPE;
      if (typeof type === 'string') {
        return type;
      }
      throw new Error(`No TYPE registered for payload ${payload.constructor?.name}`);
    }
    case 'string':
      return JavaTypes.STRING;
    case 'number':
      return JavaTypes.LONG;
    case 'boolean':
      return JavaTypes.BOOLEAN;
    default:
      return JavaTypes.STRING;
  }
}

/**
 * Converts application payloads (commands, events, queries, results) to and from the JSON
 * representation used in the Axon Server Integration HTTP API message envelopes. The fully qualified
 * type name travels with the payload, so the receiving side can reconstruct the original object.
 */
export class AxonSerializer {
  /** The JSON form of a payload object, ready to drop into a message envelope. */
  toJson(payload: unknown): Json {
    if (payload === null || payload === undefined) {
      return null;
    }
    return JSON.parse(JSON.stringify(payload));
  }

  /** Reconstructs an object from its JSON payload using the type name carried with it. */
  fromJson<T>(payload: Json, type: string): T | null {
    if (payload === null || payload === undefined) {
      return null;
    }
    // Scalars (String, Long, enums) are carried verbatim - no class to reconstruct.
    if (typeof payload !== 'object') {
      return payload as T;
    }
    const ctor = BY_TYPE.get(type);
    if (!ctor) {
      throw new Error(`Unknown payload type: ${type}`);
    }
    return Object.assign(Object.create(ctor.prototype), payload) as T;
  }

  listFromJson<T>(payload: Json, elementType: string): T[] {
    if (!Array.isArray(payload)) {
      return [];
    }
    return payload.map((element) => this.fromJson<T>(element, elementType) as T);
  }
}
