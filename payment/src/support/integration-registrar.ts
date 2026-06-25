import {
  AxonServerClient,
  ConfirmPaymentCommand,
  createLogger,
  PaymentStatusNamedQueries,
  PreparePaymentCommand,
  RejectPaymentCommand,
} from '@bikerental/core-api';

const logger = createLogger('IntegrationRegistrar');
const LOAD_FACTOR = 100;
const BATCH_SIZE = 100;

const COMMAND_URL = '/axon/command';
const QUERY_URL = '/axon/query';
const PAYMENT_PROJECTION_URL = '/axon/events/payment-status';
const GET_STATUS = 'getStatus';

/**
 * Registers the payment service with Axon Server's Integration option once it has started: an endpoint
 * where Axon Server can reach us, plus command, query and event handlers so Axon Server routes/pushes
 * messages to the controllers under `/axon/**`. The payment read model is fed by one event handler (a
 * persistent stream from `TAIL`).
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
        PreparePaymentCommand.TYPE,
        ConfirmPaymentCommand.TYPE,
        RejectPaymentCommand.TYPE,
      ]) {
        await this.client.registerCommandHandler(endpointId, this.commandHandler(command));
      }
      for (const query of [
        GET_STATUS,
        PaymentStatusNamedQueries.GET_PAYMENT_ID,
        PaymentStatusNamedQueries.GET_ALL_PAYMENTS,
      ]) {
        await this.client.registerQueryHandler(endpointId, this.queryHandler(query));
      }
      await this.client.registerEventHandler(
        endpointId,
        this.eventHandler('payment-status-projection', PAYMENT_PROJECTION_URL, 'TAIL'),
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
      eventUrl: PAYMENT_PROJECTION_URL,
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
