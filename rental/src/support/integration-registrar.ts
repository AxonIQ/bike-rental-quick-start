import {
  ApproveRequestCommand,
  AxonServerClient,
  BikeStatusNamedQueries,
  CountOfBikesByTypeQuery,
  createLogger,
  RegisterBikeCommand,
  RejectRequestCommand,
  RequestBikeCommand,
  ReturnBikeCommand,
} from '@bikerental/core-api';

const logger = createLogger('IntegrationRegistrar');
const LOAD_FACTOR = 100;
const BATCH_SIZE = 100;

const COMMAND_URL = '/axon/command';
const QUERY_URL = '/axon/query';
const BIKE_PROJECTION_URL = '/axon/events/bike-projection';
const PAYMENT_SAGA_URL = '/axon/events/payment-saga';

/**
 * Registers this application with Axon Server's Integration option once it has started: an endpoint
 * (where Axon Server can reach us) plus command, query and event handlers so Axon Server routes/pushes
 * messages to the controllers under `/axon/**`.
 *
 * The bike read model is fed by one event handler (a persistent stream from `TAIL`, so it processes
 * the full history) and the payment saga by another (from `HEAD`, so it only reacts to new requests).
 */
export class IntegrationRegistrar {
  constructor(
    private readonly client: AxonServerClient,
    private readonly endpointName: string,
    private readonly callbackUrl: string,
  ) {}

  async register(): Promise<void> {
    try {
      const endpointId = await this.client.registerEndpoint(this.endpoint());
      for (const command of [
        RegisterBikeCommand.TYPE,
        RequestBikeCommand.TYPE,
        ApproveRequestCommand.TYPE,
        RejectRequestCommand.TYPE,
        ReturnBikeCommand.TYPE,
      ]) {
        await this.client.registerCommandHandler(endpointId, this.commandHandler(command));
      }
      for (const query of [
        BikeStatusNamedQueries.FIND_ALL,
        BikeStatusNamedQueries.FIND_ONE,
        BikeStatusNamedQueries.FIND_AVAILABLE,
        CountOfBikesByTypeQuery.TYPE,
      ]) {
        await this.client.registerQueryHandler(endpointId, this.queryHandler(query));
      }
      await this.client.registerEventHandler(
        endpointId,
        this.eventHandler('rental-bike-projection', BIKE_PROJECTION_URL, 'TAIL'),
      );
      await this.client.registerEventHandler(
        endpointId,
        this.eventHandler('rental-payment-saga', PAYMENT_SAGA_URL, 'HEAD'),
      );
      logger.info(`Registered integration endpoint '${this.endpointName}' at ${this.callbackUrl}`);
    } catch (error) {
      logger.error(
        'Failed to register integration endpoint with Axon Server. Commands, queries and events ' +
          'will not be routed to this application.',
        error,
      );
    }
  }

  private endpoint(): Record<string, unknown> {
    return {
      name: this.endpointName,
      type: 'HTTP',
      wrappingType: 'Wrapped',
      baseUrl: this.callbackUrl,
      healthUrl: '/axon/health',
      commandUrl: COMMAND_URL,
      queryUrl: QUERY_URL,
      eventUrl: BIKE_PROJECTION_URL,
    };
  }

  private commandHandler(name: string): Record<string, unknown> {
    return { name, loadFactor: LOAD_FACTOR, commandUrl: COMMAND_URL };
  }

  private queryHandler(name: string): Record<string, unknown> {
    return { name, queryUrl: QUERY_URL };
  }

  private eventHandler(
    name: string,
    eventUrl: string,
    startPosition: string,
  ): Record<string, unknown> {
    return { name, eventUrl, startPosition, batchSize: BATCH_SIZE, segments: 1 };
  }
}
