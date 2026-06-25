import express from 'express';
import {
  AxonSerializer,
  AxonServerClient,
  CommandDispatcher,
  createLogger,
  QueryDispatcher,
} from '@bikerental/core-api';
import { config } from './config.js';
import { BikeCommandHandler } from './command/bike-command-handler.js';
import { BikeStatusRepository } from './query/bike-status-repository.js';
import { BikeStatusProjection } from './query/bike-status-projection.js';
import { PaymentSaga } from './paymentsaga/payment-saga.js';
import { BikeRentalDataGenerator } from './ui/bike-rental-data-generator.js';
import { Simulator } from './ui/simulator.js';
import { IntegrationRegistrar } from './support/integration-registrar.js';
import { healthRouter } from './handler/health-controller.js';
import { commandHandlerRouter } from './handler/command-handler-controller.js';
import { queryHandlerRouter } from './handler/query-handler-controller.js';
import { eventHandlerRouter } from './handler/event-handler-controller.js';
import { rentalRouter } from './ui/rental-controller.js';
import { simulatorConfigRouter } from './ui/simulator-config-controller.js';

/**
 * The Rental application. Command handling, the read model, the payment saga and the event handlers
 * are all implemented against the Axon Server Integration HTTP API (the `command`, `query`,
 * `paymentsaga`, `handler`, `ui` and `support` directories). Axon Server pushes commands, queries and
 * events to the `/axon/**` handler endpoints and serves as the event store over HTTP.
 */
const logger = createLogger('RentalApplication');

// Wiring (the manual equivalent of Spring's component scanning / dependency injection).
const client = new AxonServerClient({ httpUrl: config.axonServerHttpUrl, context: config.context });
const serializer = new AxonSerializer();
const commandDispatcher = new CommandDispatcher(client, serializer);
const queryDispatcher = new QueryDispatcher(client, serializer);

const repository = new BikeStatusRepository(config.dbFile);
const projection = new BikeStatusProjection(repository, serializer);
const saga = new PaymentSaga(commandDispatcher);
const commandHandler = new BikeCommandHandler(client, serializer);
const dataGenerator = new BikeRentalDataGenerator(commandDispatcher, queryDispatcher);

const app = express();
app.use((req, res, next) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', '*');
  res.setHeader('Access-Control-Allow-Headers', '*');
  if (req.method === 'OPTIONS') {
    res.status(200).end();
    return;
  }
  next();
});
app.use(express.json({ type: ['application/json', 'application/*+json'] }));

app.use(healthRouter());
app.use(commandHandlerRouter(commandHandler));
app.use(queryHandlerRouter(projection, serializer));
app.use(eventHandlerRouter(projection, saga, serializer));
app.use(rentalRouter(commandDispatcher, queryDispatcher, projection, dataGenerator));

let simulator: Simulator | undefined;
if (config.simulatorEnabled) {
  simulator = new Simulator(commandDispatcher, queryDispatcher, dataGenerator, config.simulator);
  app.use(simulatorConfigRouter(simulator));
}

const server = app.listen(config.port, () => {
  logger.info(`Rental service listening on port ${config.port}`);
  // Register with Axon Server once we are accepting connections (the ApplicationReadyEvent equivalent).
  void new IntegrationRegistrar(client, config.integrationName, config.callbackUrl).register();
  simulator?.start();
});

const shutdown = () => {
  simulator?.stop();
  server.close(() => process.exit(0));
};
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
