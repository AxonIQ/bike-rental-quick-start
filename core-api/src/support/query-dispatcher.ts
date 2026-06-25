import { randomUUID } from 'node:crypto';
import type { AxonServerClient } from './axon-server-client.js';
import { AxonSerializer, payloadTypeName } from './serializer.js';
import type { Query } from './messages.js';

const POLL_INTERVAL_MS = 250;

const delay = (ms: number): Promise<void> => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Thin replacement for Axon Framework's `QueryGateway`, built on the Axon Server Integration HTTP
 * API: point-to-point queries are sent with `POST /v2/queries` and routed by Axon Server to the
 * registered query handler.
 *
 * The HTTP query API does not support subscription queries. Where the Framework/gRPC branches used a
 * subscription query to wait for a value to appear, {@link pollFor} emulates it by polling the
 * point-to-point query until the result satisfies a predicate. Live UI updates for the local read
 * model are instead served by the projection's own in-memory subscription registry.
 */
export class QueryDispatcher {
  constructor(
    private readonly client: AxonServerClient,
    private readonly serializer: AxonSerializer,
  ) {}

  async query<R>(queryName: string, payload: unknown, responseType: string): Promise<R | null> {
    const result = await this.client.sendQuery(
      this.buildQuery(queryName, payload, responseType, 'SINGLE'),
    );
    return result === null ? null : this.serializer.fromJson<R>(result.payload, responseType);
  }

  async queryMany<R>(queryName: string, payload: unknown, elementType: string): Promise<R[]> {
    const result = await this.client.sendQuery(
      this.buildQuery(queryName, payload, elementType, 'MULTIPLE'),
    );
    return result === null ? [] : this.serializer.listFromJson<R>(result.payload, elementType);
  }

  /**
   * Emulates a subscription query: polls the point-to-point query at a fixed interval and resolves
   * with the first result that satisfies {@code predicate}. Used to wait for a value to appear (e.g.
   * a prepared payment's id) or for a state to be reached (e.g. a bike becoming rented).
   */
  async pollFor<R>(
    queryName: string,
    payload: unknown,
    responseType: string,
    predicate: (result: R | null) => boolean,
  ): Promise<R> {
    for (;;) {
      const result = await this.query<R>(queryName, payload, responseType);
      if (predicate(result)) {
        return result as R;
      }
      await delay(POLL_INTERVAL_MS);
    }
  }

  private buildQuery(
    queryName: string,
    payload: unknown,
    responseType: string,
    cardinality: 'SINGLE' | 'MULTIPLE',
  ): Query {
    return {
      name: queryName,
      payloadType: payload === null || payload === undefined ? undefined : payloadTypeName(payload),
      responseType,
      responseCardinality: cardinality,
      id: randomUUID(),
      payload: payload === null || payload === undefined ? undefined : this.serializer.toJson(payload),
    };
  }
}
