// The JSON message envelopes of the Axon Server Integration HTTP API (the "Wrapped" form), used both
// when sending messages to Axon Server and when Axon Server pushes messages back to the handler
// endpoints we register. `payloadType`/`name` carry the (Java) type name and `payload` the JSON form
// of the object, so the receiving side can reconstruct it. Absent fields are left `undefined` so they
// drop out of the JSON we send (the equivalent of Jackson's `@JsonInclude(NON_NULL)`).

/** A parsed JSON payload, equivalent to Jackson's `JsonNode`. */
export type Json = unknown;

export interface Command {
  name?: string;
  payloadType?: string;
  payloadRevision?: string;
  routingKey?: string;
  priority?: number;
  id?: string;
  metaData?: Record<string, unknown>;
  payload?: Json;
}

export interface CommandResult {
  id?: string;
  payloadType?: string;
  payloadRevision?: string;
  metaData?: Record<string, unknown>;
  payload?: Json;
}

export interface Query {
  name?: string;
  payloadType?: string;
  payloadRevision?: string;
  responseType?: string;
  responseCardinality?: string;
  numberOfResponses?: number;
  id?: string;
  metaData?: Record<string, unknown>;
  payload?: Json;
}

export interface QueryResult {
  id?: string;
  payloadType?: string;
  payloadRevision?: string;
  metaData?: Record<string, unknown>;
  payload?: Json;
}

export interface Event {
  name?: string;
  payloadType?: string;
  payloadRevision?: string;
  id?: string;
  aggregateId?: string;
  aggregateType?: string;
  sequenceNumber?: number;
  index?: number;
  dateTime?: string;
  metaData?: Record<string, unknown>;
  payload?: Json;
}

/** The event type, tolerating both field spellings used across the API surface. */
export function eventType(event: Event): string {
  return event.payloadType ?? event.name ?? '';
}

/** The fully qualified Java type names used for scalar payloads/responses. */
export const JavaTypes = {
  STRING: 'java.lang.String',
  LONG: 'java.lang.Long',
  INTEGER: 'java.lang.Integer',
  BOOLEAN: 'java.lang.Boolean',
} as const;
