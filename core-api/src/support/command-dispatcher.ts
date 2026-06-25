import { randomUUID } from 'node:crypto';
import type { AxonServerClient } from './axon-server-client.js';
import { AxonSerializer, payloadTypeName } from './serializer.js';
import type { Command } from './messages.js';

/**
 * Thin replacement for Axon Framework's `CommandGateway`, built on the Axon Server Integration HTTP
 * API: a command is sent with `POST /v2/commands` and routed by Axon Server to the registered command
 * handler. Its `name`/`payloadType` is the payload's type name (how the handler is registered) and
 * its `payload` the JSON form of the command object.
 */
export class CommandDispatcher {
  constructor(
    private readonly client: AxonServerClient,
    private readonly serializer: AxonSerializer,
  ) {}

  send(payload: object): Promise<void>;
  send<R>(payload: object, responseType: string): Promise<R | null>;
  async send<R>(payload: object, responseType?: string): Promise<R | null | void> {
    const type = payloadTypeName(payload);
    const command: Command = {
      name: type,
      payloadType: type,
      id: randomUUID(),
      payload: this.serializer.toJson(payload),
    };
    const result = await this.client.sendCommand(command);
    if (responseType === undefined || result === null || result.payload === null) {
      return null;
    }
    return this.serializer.fromJson<R>(result.payload, responseType);
  }
}
