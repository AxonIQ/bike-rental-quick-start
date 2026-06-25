import express from 'express';
import {
  AxonSerializer,
  AxonServerClient,
  CommandDispatcher,
  createLogger,
  QueryDispatcher,
} from '@bikerental/core-api';
import { config } from './config.js';
import { PaymentCommandHandler } from './payment-command-handler.js';
import { PaymentStatusRepository } from './payment-status-repository.js';
import { PaymentStatusProjection } from './payment-status-projection.js';
import { IntegrationRegistrar } from './support/integration-registrar.js';
import { healthRouter } from './handler/health-controller.js';
import { commandHandlerRouter } from './handler/command-handler-controller.js';
import { queryHandlerRouter } from './handler/query-handler-controller.js';
import { eventHandlerRouter } from './handler/event-handler-controller.js';
import { paymentRouter } from './payment-controller.js';

/**
 * The Payment application. Command handling and the read model are implemented against the Axon Server
 * Integration HTTP API (the `support` and `handler` modules): Axon Server pushes commands, queries and
 * events to the `/axon/**` endpoints and serves as the event store over HTTP.
 */
const logger = createLogger('PaymentApplication');

const client = new AxonServerClient({ httpUrl: config.axonServerHttpUrl, context: config.context });
const serializer = new AxonSerializer();
const commandDispatcher = new CommandDispatcher(client, serializer);
const queryDispatcher = new QueryDispatcher(client, serializer);

const repository = new PaymentStatusRepository(config.dbFile);
const projection = new PaymentStatusProjection(repository, serializer);
const commandHandler = new PaymentCommandHandler(client, serializer);

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
app.use(eventHandlerRouter(projection, serializer));
app.use(paymentRouter(queryDispatcher, commandDispatcher));

const server = app.listen(config.port, () => {
  logger.info(`Payment service listening on port ${config.port}`);
  void new IntegrationRegistrar(client, config.integrationName, config.callbackUrl).register();
});

const shutdown = () => server.close(() => process.exit(0));
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
