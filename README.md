# Evolving a service from monolith to microservices with Axon Framework and Axon Server

> **Note — `as-http-api` branch:** on this branch the Rental and Payment services are rebuilt on the
> **Axon Server [Integration HTTP API](https://docs.axoniq.io/axon-server-reference/v2026.0/axon-server/administration/integration/)**
> only — no Axon Framework and no gRPC client (`axonserver-connector-java`) at all. The only Axon
> dependency is plain HTTP, made with Spring's `RestClient`.
>
> The Integration option is the HTTP equivalent of the gRPC channels: each service **registers an
> endpoint and handlers** with Axon Server on startup (`POST /v2/endpoints` and `.../commandHandlers`,
> `.../queryHandlers`, `.../eventHandlers` — see `IntegrationRegistrar`), and Axon Server then
> **pushes** commands, queries and events to the service's HTTP handler endpoints (the controllers
> under `/axon/**`). The services **send** messages with `POST /v2/commands|queries|events` and **load
> aggregates** with `GET /v2/aggregates/{id}/events`.
>
> | Capability | Framework (main) | gRPC (`as-grpc-api`) | Integration HTTP (this branch) |
> |---|---|---|---|
> | Command handling | `@Aggregate` + `CommandGateway` | command channel | command handler registered with Axon Server → it `POST`s to `/axon/command`; aggregate rebuilt via `GET /v2/aggregates/{id}/events`, new events appended via `POST /v2/events` (see `*CommandHandler`, `CommandHandlerController`) |
> | Queries | `@QueryHandler` + `QueryGateway` | query channel | query handler registered → Axon Server `POST`s to `/axon/query`; sent via `POST /v2/queries` (see `*Projection`, `QueryHandlerController`, `QueryDispatcher`) |
> | Subscription queries | `QueryUpdateEmitter` | subscription query | **not supported by the HTTP API** — served in-process from the read model (live UI updates via a Reactor `SubscriptionRegistry`; cross-service waits via short-interval polling, see `QueryDispatcher#subscriptionQuery`) |
> | Event processors | tracking processors + token store | `openStream(token,…)` | Integration **event handlers** = persistent streams whose position Axon Server tracks server-side; it `POST`s event batches to `/axon/events/*` (see `EventHandlerController`) |
> | Saga + deadlines | `@Saga` + `DeadlineManager` | in-memory state machine | unchanged in-memory state machine + `ScheduledExecutorService`, fed by an event-handler callback (see `PaymentSaga`) |
>
> Spring Boot still provides the web layer and the JPA-backed read models. The `microservices`
> module still relies on the Axon Framework and is excluded from the build on this branch.
>
> **Reachability note:** Axon Server must be able to reach each service to push messages to it. Set
> `axon.integration.callback-url` (in each `application.properties`) to a URL reachable from wherever
> Axon Server runs — when Axon Server runs in Docker and the apps run on the host, that means
> `http://host.docker.internal:8080` (rental) and `:8081` (payment). The `axon.axonserver.http-url`
> (default `http://localhost:8024`) points the other way, at Axon Server's HTTP port.

The goal of this repo is to show how one can develop a well structured monolithic application that can evolve to become a set of microservices
using [Axon Framework and Axon Server](https://developer.axoniq.io/).

This starts as two services, Rental (Monolith) and Payment which work together to run the Axoniq World Wide Bike Rental Service. 
The Rental service manages the inventory and rental status of bikes.  While the Payment service manages payment processing related to 
a bike rental.  

![Axoniq World Wide Bike Rental Architecture](/images/Bike-Rental-Quick-Start.monolith.png)

## Pre-requisites

The following software must be installed in your local environment:

* JDK version 21.

* Docker-Compose

## Quick Start

* An IDE such as [Jetbrains IDEA](https://www.jetbrains.com/idea/) is recommended.
### Start Services
Begin by running the `PaymentApplication` and `RentalApplication` Services in this order.  
This will start a docker image of Axon-Server using run the docker-compose.yaml file found in the root of the project. 
Once you have both services started you can see them connected to Axon-Server at http://localhost:8024/#overview
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


## Evolving Rental Application monolith to microservices
Great news!  The Axoniq World Wide Bike Rental Service is renting bikes faster than we can buy them!  As a result our
Rental Application is experiencing some scalability issues.  To handle this increase in volume on our application it has
been determined that we need to break out the parts of the Rental Application each in to their own service.  Our updated
architecture now looks like the following...![Axoniq World Wide Bike Rental Microservices Architecture](/images/Bike-Rental-Quick-Start.microservices.png)

To make this happen run the [create-microservices.sh](create-microservices.sh) script to copy the necessary files into 
the pre-defined services in the project. Once the script is complete, you must stop the running `RentalApplication` app (port conflict),
and then run the new services `RentalCommandApplication`, `RentalPaymentSagaApplication`, `RentalQueryApplication`, and `UserInterfaceApplication`.

This allows us to run each aspect of our Rental domain as an independent service with no functional changes to the code base. 
Our initial approach of using features in Axon Framework such as Command Gateway and Query Gateway have provided us with
location independence between our components.   We are now able to evolve and scale each component as necessary to handle 
the increased load of our ever growing bike rental business.


## Monitoring our Axon Framework and Axon Server based services
To be able to understand the performance of our services, we can use the [Axoniq Console](https://console.axoniq.io). Using 
Axoniq Console we can register each of our microservices, check on performance command handling within our Aggregates (Bike and Payment),
query handling performance, and event processors as well. 
