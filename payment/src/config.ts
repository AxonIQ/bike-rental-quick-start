// Application configuration, read from the environment with the same defaults the Java service used
// in its application.properties. When Axon Server runs in Docker and this app runs on the host, set
// AXON_INTEGRATION_CALLBACK_URL to http://host.docker.internal:8081.

const env = process.env;

export const config = {
  port: Number(env.SERVER_PORT ?? 8081),

  // Axon Server Integration HTTP API - where we send commands/queries/events and read aggregates.
  axonServerHttpUrl: env.AXON_AXONSERVER_HTTP_URL ?? 'http://localhost:8024',
  context: env.AXON_AXONSERVER_CONTEXT ?? 'default',

  // How this application identifies itself to Axon Server, and the base URL at which Axon Server can
  // reach it to push commands, queries and events to the /axon/** handler endpoints.
  integrationName: env.AXON_INTEGRATION_NAME ?? 'payment',
  callbackUrl: env.AXON_INTEGRATION_CALLBACK_URL ?? 'http://localhost:8081',

  // Read-model persistence: an embedded SQLite database (the stand-in for the Java service's H2).
  dbFile: env.PAYMENT_DB_FILE ?? '.db/payment.sqlite',
};
