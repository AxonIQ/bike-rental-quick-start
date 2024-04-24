package io.axoniq.demo.bikerental.rental.ui;

import io.axoniq.demo.bikerental.coreapi.payment.ConfirmPaymentCommand;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatusNamedQueries;
import io.axoniq.demo.bikerental.coreapi.rental.*;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@CrossOrigin(origins = "*", maxAge = 3600)
// tag::RentalControllerClassDefinition[]
@RestController      // <.>
@RequestMapping("/") // <.>
public class RentalController {
// end::RentalControllerClassDefinition[]

//    public static final String FIND_ALL_QUERY = "findAll";
//    public static final String FIND_ONE_QUERY = "findOne";

    //tag::ControllerInitialization[]
    //tag::BusGateways[]

    private final CommandGateway commandGateway;    // <.>
    private final QueryGateway queryGateway;        // <.>
    //end::BusGateways[]

    private final BikeRentalDataGenerator bikeRentalDataGenerator;

    public RentalController(CommandGateway commandGateway, QueryGateway queryGateway, BikeRentalDataGenerator bikeRentalDataGenerator) { // <.>
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
        this.bikeRentalDataGenerator = bikeRentalDataGenerator;
    }

    //end::ControllerInitialization[]
    //tag::registerBike[]
    @PostMapping("/bikes") // <.>
    public CompletableFuture<String> registerBike(
            @RequestParam("bikeType") String bikeType,      // <.>
            @RequestParam("location") String location) {    // <.>

        RegisterBikeCommand registerBikeCommand =
                new RegisterBikeCommand(                // <.>
                        UUID.randomUUID().toString(),   // <.>
                        bikeType,
                        location);

        CompletableFuture<String> commandResult =
                commandGateway.send(registerBikeCommand); //<.>

        return commandResult; // <.>
    }

    //end::registerBike[]
    //tag::generateBikes[]
    @PostMapping("/bikes/batch") // <.>
    public CompletableFuture<Void> generateBikes(@RequestParam("count") int bikeCount,              //<.>
                                                 @RequestParam(value = "type") String bikeType) {   //<.>
        CompletableFuture<Void> all = CompletableFuture.completedFuture(null);
        for (int i = 0; i < bikeCount; i++) {
            all = CompletableFuture.allOf(all,
                                          commandGateway.send(new RegisterBikeCommand(UUID.randomUUID().toString(), bikeType, this.bikeRentalDataGenerator.randomLocation())));
        }
        return all;
    }

    //end::generateBikes[]
    //tag::findAll[]
    @GetMapping("/bikes") //<.>
    public CompletableFuture<List<BikeStatus>> findAll() { //<.>
        return queryGateway.query( //<.>
                BikeStatusNamedQueries.FIND_ALL, //<.>
                null, //<.>
                ResponseTypes.multipleInstancesOf(BikeStatus.class) //<.>
        );
    }
    //end::findAll[]
    @GetMapping("/bikeUpdates")
    public Flux<ServerSentEvent<String>> subscribeToAllUpdates() {
        SubscriptionQueryResult<List<BikeStatus>, BikeStatus> subscriptionQueryResult = queryGateway.subscriptionQuery(BikeStatusNamedQueries.FIND_ALL, null, ResponseTypes.multipleInstancesOf(BikeStatus.class), ResponseTypes.instanceOf(BikeStatus.class));
        return subscriptionQueryResult.initialResult()
                                      .flatMapMany(Flux::fromIterable)
                                      .concatWith(subscriptionQueryResult.updates())
                                      .doFinally(s -> subscriptionQueryResult.close())
                                      .map(BikeStatus::description)
                                      .map(description -> ServerSentEvent.builder(description).build());
    }


    @GetMapping("/bikeUpdatesJson")
    public Flux<ServerSentEvent<BikeStatus>> subscribeToAllUpdatesJson() {
        SubscriptionQueryResult<List<BikeStatus>, BikeStatus> subscriptionQueryResult = queryGateway.subscriptionQuery(
                BikeStatusNamedQueries.FIND_ALL,
                null,
                ResponseTypes.multipleInstancesOf(BikeStatus.class),
                ResponseTypes.instanceOf(BikeStatus.class));
        return subscriptionQueryResult.initialResult()
                .flatMapMany(Flux::fromIterable)
                .concatWith(subscriptionQueryResult.updates())
                .doFinally(s -> subscriptionQueryResult.close())
                .map(description -> ServerSentEvent.builder(description).build());
    }

    @GetMapping("/bikeUpdates/{bikeId}")
    public Flux<ServerSentEvent<String>> subscribeToBikeUpdates(@PathVariable("bikeId") String bikeId) {
        SubscriptionQueryResult<BikeStatus, BikeStatus> subscriptionQueryResult = queryGateway.subscriptionQuery(BikeStatusNamedQueries.FIND_ONE, bikeId, BikeStatus.class, BikeStatus.class);
        return subscriptionQueryResult.initialResult()
                                      .concatWith(subscriptionQueryResult.updates())
                                      .doFinally(s -> subscriptionQueryResult.close())
                                      .map(BikeStatus::description)
                                      .map(description -> ServerSentEvent.builder(description).build());
    }

    @PostMapping("/requestBike")
    public CompletableFuture<String> requestBike(@RequestParam("bikeId") String bikeId, @RequestParam(value = "renter", required = false) String renter) {
        return commandGateway.send(new RequestBikeCommand(bikeId, renter != null ? renter : this.bikeRentalDataGenerator.randomRenter()));
    }

    @PostMapping("/returnBike")
    public CompletableFuture<String> returnBike(@RequestParam("bikeId") String bikeId) {
        return commandGateway.send(new ReturnBikeCommand(bikeId, this.bikeRentalDataGenerator.randomLocation()));
    }

    @GetMapping("findPayment")
    public Mono<String> getPaymentId(@RequestParam("reference") String paymentRef) {
        SubscriptionQueryResult<String, String> queryResult = queryGateway.subscriptionQuery(PaymentStatusNamedQueries.GET_PAYMENT_ID, paymentRef, String.class, String.class);
        return queryResult.initialResult().concatWith(queryResult.updates())
                          .filter(Objects::nonNull)
                          .next();

    }

    @GetMapping("pendingPayments")
    public CompletableFuture<PaymentStatus> getPendingPayments() {
        return queryGateway.query(PaymentStatusNamedQueries.GET_ALL_PAYMENTS, PaymentStatus.Status.PENDING, PaymentStatus.class);
    }

    @PostMapping("acceptPayment")
    public CompletableFuture<Void> acceptPayment(@RequestParam("id") String paymentId) {
        return commandGateway.send(new ConfirmPaymentCommand(paymentId));
    }


    @GetMapping(value = "watch", produces = "text/event-stream")
    public Flux<String> watchAll() {
        SubscriptionQueryResult<List<BikeStatus>, BikeStatus> subscriptionQuery = queryGateway.subscriptionQuery(BikeStatusNamedQueries.FIND_ALL, null, ResponseTypes.multipleInstancesOf(BikeStatus.class), ResponseTypes.instanceOf(BikeStatus.class));
        return subscriptionQuery.initialResult()
                                .flatMapMany(Flux::fromIterable)
                                .concatWith(subscriptionQuery.updates())
                                .map(bs -> bs.getBikeId() + " -> " + bs.description());
    }

    @GetMapping(value = "watch/{bikeId}", produces = "text/event-stream")
    public Flux<String> watchBike(@PathVariable("bikeId") String bikeId) {
        SubscriptionQueryResult<BikeStatus, BikeStatus> subscriptionQuery = queryGateway.subscriptionQuery(BikeStatusNamedQueries.FIND_ONE, bikeId, ResponseTypes.instanceOf(BikeStatus.class), ResponseTypes.instanceOf(BikeStatus.class));
        return subscriptionQuery.initialResult()
                                .concatWith(subscriptionQuery.updates())
                                .map(bs -> bs.getBikeId() + " -> " + bs.description());
    }


    @PostMapping(value = "/generateRentals")
    public Flux<String> generateData(@RequestParam(value = "bikeType") String bikeType,
                                     @RequestParam("loops") int loops,
                                     @RequestParam(value = "concurrency", defaultValue = "1") int concurrency,
                                     @RequestParam(value = "abandonPaymentFactor", defaultValue = "100") int abandonPaymentFactor,
                                     @RequestParam(value = "delay", defaultValue = "0")int delay) {

          return this.bikeRentalDataGenerator.generateRentals(bikeType, loops, concurrency, abandonPaymentFactor, delay);
    }

    //tag::findOneQuery[]
    @GetMapping("/bikes/{bikeId}") // <.>
    public CompletableFuture<BikeStatus> findStatus(@PathVariable("bikeId") String bikeId) { //<.>
        return queryGateway.query(BikeStatusNamedQueries.FIND_ONE, bikeId, BikeStatus.class); //<.>
    }

    //end::findOneQuery[]
}

