# Evolving a service from monolith to microservices with Axon Framework and Axon Server

> **Note — `as-http-ts-api` branch:** this is a **TypeScript / Node.js** rewrite of the `as-http-api`
> branch. The Rental and Payment services keep exactly the same logic and talk to Axon Server over its
> **[Integration HTTP API](https://docs.axoniq.io/axon-server-reference/v2026.0/axon-server/administration/integration/)**
> only — no Axon Framework and no gRPC client. There is no Java and no Spring here: the web layer is
> [Express](https://expressjs.com/), the Axon Server client is plain `fetch`, and the read models are
> persisted to an embedded [SQLite](https://www.sqlite.org/) database via
> [`better-sqlite3`](https://github.com/WiseLibs/better-sqlite3) (the stand-in for the H2 database the
> Java branch used).
>
> The Integration option is the HTTP equivalent of the gRPC channels: each service **registers an
> endpoint and handlers** with Axon Server on startup (`POST /v2/endpoints` and `.../commandHandlers`,
> `.../queryHandlers`, `.../eventHandlers` — see `IntegrationRegistrar`), and Axon Server then
> **pushes** commands, queries and events to the service's HTTP handler endpoints (the controllers
> under `/axon/**`). The services **send** messages with `POST /v2/commands|queries|events` and **load
> aggregates** with `GET /v2/aggregates/{id}/events`.
>
> | Capability | Framework (main) | gRPC (`as-grpc-api`) | Integration HTTP / TypeScript (this branch) |
> |---|---|---|---|
> | Command handling | `@Aggregate` + `CommandGateway` | command channel | command handler registered with Axon Server → it `POST`s to `/axon/command`; aggregate rebuilt via `GET /v2/aggregates/{id}/events`, new events appended via `POST /v2/events` (see `*CommandHandler`, `command-handler-controller.ts`) |
> | Queries | `@QueryHandler` + `QueryGateway` | query channel | query handler registered → Axon Server `POST`s to `/axon/query`; sent via `POST /v2/queries` (see `*Projection`, `query-handler-controller.ts`, `QueryDispatcher`) |
> | Subscription queries | `QueryUpdateEmitter` | subscription query | **not supported by the HTTP API** — served in-process from the read model (live UI updates via an in-memory `SubscriptionRegistry`; cross-service waits via short-interval polling, see `QueryDispatcher.pollFor`) |
> | Event processors | tracking processors + token store | `openStream(token,…)` | Integration **event handlers** = persistent streams whose position Axon Server tracks server-side; it `POST`s event batches to `/axon/events/*` (see `event-handler-controller.ts`) |
> | Saga + deadlines | `@Saga` + `DeadlineManager` | in-memory state machine | unchanged in-memory state machine driven by an event-handler callback, with `setTimeout` deadlines (see `PaymentSaga`) |
>
> The repository is an npm workspaces monorepo: `core-api` (shared domain messages + the Axon Server
> HTTP client), `payment` and `rental`.
>
> **Reachability note:** Axon Server must be able to reach each service to push messages to it. Set
> `AXON_INTEGRATION_CALLBACK_URL` to a URL reachable from wherever Axon Server runs — when Axon Server
> runs in Docker and the apps run on the host, that means `http://host.docker.internal:8080` (rental)
> and `:8081` (payment). `AXON_AXONSERVER_HTTP_URL` (default `http://localhost:8024`) points the other
> way, at Axon Server's HTTP port.

The goal of this repo is to show how one can develop a well structured monolithic application that can evolve to become a set of microservices
using [Axon Framework and Axon Server](https://developer.axoniq.io/).

This starts as two services, Rental (Monolith) and Payment which work together to run the Axoniq World Wide Bike Rental Service. 
The Rental service manages the inventory and rental status of bikes.  While the Payment service manages payment processing related to 
a bike rental.  

![Axoniq World Wide Bike Rental Architecture](/images/Bike-Rental-Quick-Start.monolith.png)

## Pre-requisites

The following software must be installed in your local environment:

* Node.js version 20 (or newer).

* Docker Compose

## Quick Start

### Start Axon Server
Start Axon Server with the `compose.yaml` in the root of the project:

```shell
docker compose up -d
```

Once it is up you can reach its dashboard at http://localhost:8024/#overview

### Build and start the services
Install the dependencies and build all workspaces once:

```shell
npm install
npm run build
```

Then start the **Payment** and **Rental** services (in two terminals, in this order):

```shell
npm run start:payment   # listens on :8081
npm run start:rental    # listens on :8080
```

(During development you can use `npm run dev` inside the `payment`/`rental` workspaces to run the
TypeScript sources directly without a build step.)

Each service registers itself with Axon Server on startup; you can then see them connected at
http://localhost:8024/#overview
![Axon Server Overview](/images/Bike-Rental-Quick-Start-AxonServer-Overview.png)

From this page you are able to navigate to the details for each application by clicking on the application in the diagram.
Once on the details page for an application you are able to see the list of connected application instances, 
list of handled commands, list of handled queries, and running event processors


## Running our business
### Populate Inventory of Bikes
In order to begin offering our bike rental service we will need an inventory of bikes.  To do this, navigate to the
[requests.http](./requests.http) file, find the section with the header ```### Generate bikes``` and executing the http 
```POST``` command shown.  This will give you an inventory of bikes which you can verify by executing the http command
found in the```### List all``` section of [requests.http](/requests.http) file.


### Generate Bike Rentals
Now that your inventory is in place it is time to make some money!!  To simulate all the steps of a rental and return 
(request a bike, completing payment, unlocking, and finally returning) we can execute the http command found at the header
```### Generate Rentals``` of the of [requests.http](/requests.http) file.


## Simulator

The Rental service can continuously generate inventory and rentals on its own. Start it with the
`simulator` profile to top up the bike inventory and run rental cycles every 25 seconds:

```shell
SPRING_PROFILES_ACTIVE=simulator npm run start:rental
```

The simulation parameters (inventory size, bike type, loops, concurrency, …) can be tuned with the
`INVENTORY_*` and `RENTAL_SIMULATION_*` environment variables (see `rental/src/config.ts`) or
reconfigured at runtime via the `/inventoryGenerationConfig` and `/rentalGenerationConfig` endpoints.

## Evolving to microservices

The Framework-based branches split the Rental monolith into separate Command, Query, Payment-saga and
UI deployables. That decomposition is out of scope for this TypeScript / Integration-HTTP branch,
which keeps Rental and Payment as the two services.
