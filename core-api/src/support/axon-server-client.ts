import { createLogger } from './logger.js';
import { CommandExecutionException, QueryExecutionException } from './errors.js';
import type {
  Command,
  CommandResult,
  Event,
  Query,
  QueryResult,
} from './messages.js';

const logger = createLogger('AxonServerClient');

// The media type the Axon Server event-publish endpoint consumes (per its OpenAPI definition).
const EVENT_LIST_JSON = 'application/vnd.axoniq.event.list+json';

export interface AxonServerClientOptions {
  /** Axon Server's HTTP base URL, e.g. http://localhost:8024. */
  httpUrl: string;
  /** The context all calls target. */
  context: string;
}

/**
 * Thin client over the Axon Server Integration HTTP API: send commands and queries, publish and read
 * events, and register this application's handler endpoints so Axon Server pushes messages to them.
 * All calls target a single context and are plain HTTP.
 */
export class AxonServerClient {
  private readonly baseUrl: string;
  private readonly context: string;

  constructor(options: AxonServerClientOptions) {
    this.baseUrl = options.httpUrl.replace(/\/+$/, '');
    this.context = options.context;
  }

  // -------------------------------------------------------------------------------------------
  // Messaging: send commands / queries, publish & read events
  // -------------------------------------------------------------------------------------------

  async sendCommand(command: Command): Promise<CommandResult> {
    const response = await fetch(this.url('/v2/commands'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(command),
    });
    if (!response.ok) {
      throw new CommandExecutionException('AXONIQ-HTTP', await readError(response));
    }
    return (await response.json()) as CommandResult;
  }

  async sendQuery(query: Query): Promise<QueryResult> {
    const response = await fetch(this.url('/v2/queries'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(query),
    });
    if (!response.ok) {
      throw new QueryExecutionException(await readError(response));
    }
    return (await response.json()) as QueryResult;
  }

  /** Appends events in a single transaction (POST /v2/events with the wrapped JSON list form). */
  async appendEvents(events: Event[]): Promise<void> {
    if (events.length === 0) {
      return;
    }
    const response = await fetch(this.url('/v2/events'), {
      method: 'POST',
      headers: { 'Content-Type': EVENT_LIST_JSON },
      body: JSON.stringify(events),
    });
    if (!response.ok) {
      throw new Error(`Appending events failed: ${await readError(response)}`);
    }
  }

  /**
   * Reads an aggregate's event stream (ascending sequence), used to rebuild a decision model. A
   * not-yet-existing aggregate (no events) yields an empty list.
   */
  async readAggregateEvents(aggregateId: string): Promise<Event[]> {
    const response = await fetch(
      this.url(`/v2/aggregates/${encodeURIComponent(aggregateId)}/events`),
    );
    if (response.status === 404) {
      return [];
    }
    if (!response.ok) {
      throw new Error(`Reading aggregate ${aggregateId} failed: ${await readError(response)}`);
    }
    const events = (await response.json()) as Event[] | null;
    return events ?? [];
  }

  // -------------------------------------------------------------------------------------------
  // Integration registration: tell Axon Server where to push commands/queries/events
  // -------------------------------------------------------------------------------------------

  /** Registers (or looks up an existing) endpoint and returns its id. */
  async registerEndpoint(endpoint: Record<string, unknown>): Promise<string> {
    const name = String(endpoint.name);
    const existing = await this.findEndpointId(name);
    if (existing) {
      logger.info(`Integration endpoint '${name}' already registered (${existing})`);
      return existing;
    }
    const response = await fetch(this.url('/v2/endpoints'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(endpoint),
    });
    if (!response.ok) {
      throw new Error(`Registering endpoint '${name}' failed: ${await readError(response)}`);
    }
    const id = await this.findEndpointId(name);
    if (!id) {
      throw new Error(`Endpoint '${name}' was not found after registration`);
    }
    return id;
  }

  async findEndpointId(name: string): Promise<string | undefined> {
    const response = await fetch(this.url('/v2/endpoints'));
    if (!response.ok) {
      throw new Error(`Listing endpoints failed: ${await readError(response)}`);
    }
    const endpoints = (await response.json()) as Record<string, unknown>[] | null;
    return endpoints
      ?.find((e) => e.name === name && e.context === this.context)
      ?.id?.toString();
  }

  registerCommandHandler(endpointId: string, handler: Record<string, unknown>): Promise<void> {
    return this.registerHandler(endpointId, 'commandHandlers', handler);
  }

  registerQueryHandler(endpointId: string, handler: Record<string, unknown>): Promise<void> {
    return this.registerHandler(endpointId, 'queryHandlers', handler);
  }

  registerEventHandler(endpointId: string, handler: Record<string, unknown>): Promise<void> {
    return this.registerHandler(endpointId, 'eventHandlers', handler);
  }

  private async registerHandler(
    endpointId: string,
    kind: string,
    handler: Record<string, unknown>,
  ): Promise<void> {
    try {
      const response = await fetch(this.url(`/v2/endpoints/${endpointId}/${kind}`), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(handler),
      });
      if (!response.ok) {
        // Most likely the handler is already registered from a previous run - safe to ignore.
        logger.info(`Skipping ${kind} '${handler.name}' registration: ${response.statusText}`);
        return;
      }
      logger.info(`Registered ${kind} '${handler.name}'`);
    } catch (error) {
      logger.info(`Skipping ${kind} '${handler.name}' registration: ${String(error)}`);
    }
  }

  private url(path: string): string {
    return `${this.baseUrl}${path}?context=${encodeURIComponent(this.context)}`;
  }
}

async function readError(response: Response): Promise<string> {
  try {
    const body = await response.text();
    return body.length === 0 ? response.statusText : body;
  } catch {
    return response.statusText;
  }
}
