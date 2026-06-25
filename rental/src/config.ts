// Application configuration, read from the environment with the same defaults the Java service used
// in its application.properties / application-simulator.properties. When Axon Server runs in Docker
// and this app runs on the host, set AXON_INTEGRATION_CALLBACK_URL to http://host.docker.internal:8080.

const env = process.env;

function activeProfiles(): string[] {
  return (env.SPRING_PROFILES_ACTIVE ?? '')
    .split(',')
    .map((p) => p.trim())
    .filter(Boolean);
}

export const config = {
  port: Number(env.SERVER_PORT ?? 8080),

  // Axon Server Integration HTTP API - where we send commands/queries/events and read aggregates.
  axonServerHttpUrl: env.AXON_AXONSERVER_HTTP_URL ?? 'http://localhost:8024',
  context: env.AXON_AXONSERVER_CONTEXT ?? 'default',

  // How this application identifies itself to Axon Server, and the base URL at which Axon Server can
  // reach it to push commands, queries and events to the /axon/** handler endpoints.
  integrationName: env.AXON_INTEGRATION_NAME ?? 'rental',
  callbackUrl: env.AXON_INTEGRATION_CALLBACK_URL ?? 'http://localhost:8080',

  // Read-model persistence: an embedded SQLite database (the stand-in for the Java service's H2).
  dbFile: env.RENTAL_DB_FILE ?? '.db/rental.sqlite',

  // The 'simulator' profile drives continuous bike/rental generation.
  simulatorEnabled: activeProfiles().includes('simulator'),
  simulator: {
    inventorySize: Number(env.INVENTORY_SIZE ?? 100),
    inventoryBikeType: env.INVENTORY_BIKE_TYPE ?? 'mountain bike',
    rentalBikeType: env.RENTAL_SIMULATION_BIKE_TYPE ?? 'mountain bike',
    loops: Number(env.RENTAL_SIMULATION_LOOPS ?? 64),
    concurrency: Number(env.RENTAL_SIMULATION_CONCURRENCY ?? 8),
    abandonPaymentFactor: Number(env.RENTAL_SIMULATION_ABANDON_PAYMENT_FACTOR ?? 0),
    delayBetweenLoops: Number(env.RENTAL_SIMULATION_DELAY_BETWEEN_LOOPS ?? 1000),
  },
};
