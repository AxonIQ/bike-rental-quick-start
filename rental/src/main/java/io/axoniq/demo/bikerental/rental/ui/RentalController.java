package io.axoniq.demo.bikerental.rental.ui;

import io.axoniq.demo.bikerental.coreapi.payment.ConfirmPaymentCommand;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatusNamedQueries;
import io.axoniq.demo.bikerental.coreapi.rental.BikeStatus;
import io.axoniq.demo.bikerental.coreapi.rental.BikeStatusNamedQueries;
import io.axoniq.demo.bikerental.coreapi.rental.RegisterBikeCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RequestBikeCommand;
import io.axoniq.demo.bikerental.coreapi.rental.ReturnBikeCommand;
import io.axoniq.demo.bikerental.rental.support.CommandDispatcher;
import io.axoniq.demo.bikerental.rental.support.QueryDispatcher;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/")
public class RentalController {

    private final CommandDispatcher commandDispatcher;
    private final QueryDispatcher queryDispatcher;
    private final BikeRentalDataGenerator bikeRentalDataGenerator;

    public RentalController(CommandDispatcher commandDispatcher,
                            QueryDispatcher queryDispatcher,
                            BikeRentalDataGenerator bikeRentalDataGenerator) {
        this.commandDispatcher = commandDispatcher;
        this.queryDispatcher = queryDispatcher;
        this.bikeRentalDataGenerator = bikeRentalDataGenerator;
    }

    @PostMapping("/bikes")
    public CompletableFuture<String> registerBike(@RequestParam("bikeType") String bikeType,
                                                  @RequestParam("location") String location) {
        String bikeId = UUID.randomUUID().toString();
        return commandDispatcher.send(new RegisterBikeCommand(bikeId, bikeType, location))
                                .thenApply(unused -> bikeId);
    }

    @PostMapping("/bikes/batch")
    public CompletableFuture<Void> generateBikes(@RequestParam("count") int bikeCount,
                                                 @RequestParam(value = "type") String bikeType) {
        return bikeRentalDataGenerator.generateBikes(bikeCount, bikeType);
    }

    @GetMapping("/bikes")
    public CompletableFuture<List<BikeStatus>> findAll() {
        return queryDispatcher.queryMany(BikeStatusNamedQueries.FIND_ALL, null, BikeStatus.class);
    }

    @GetMapping("/bikeUpdates")
    public Flux<ServerSentEvent<String>> subscribeToAllUpdates() {
        return queryDispatcher.subscriptionQueryMany(BikeStatusNamedQueries.FIND_ALL, null, BikeStatus.class)
                              .map(BikeStatus::description)
                              .map(description -> ServerSentEvent.builder(description).build());
    }

    @GetMapping("/bikeUpdatesJson")
    public Flux<ServerSentEvent<BikeStatus>> subscribeToAllUpdatesJson() {
        return queryDispatcher.subscriptionQueryMany(BikeStatusNamedQueries.FIND_ALL, null, BikeStatus.class)
                              .map(status -> ServerSentEvent.builder(status).build());
    }

    @GetMapping("/bikeUpdates/{bikeId}")
    public Flux<ServerSentEvent<String>> subscribeToBikeUpdates(@PathVariable("bikeId") String bikeId) {
        return queryDispatcher.subscriptionQuery(BikeStatusNamedQueries.FIND_ONE, bikeId, BikeStatus.class)
                              .map(BikeStatus::description)
                              .map(description -> ServerSentEvent.builder(description).build());
    }

    @PostMapping("/requestBike")
    public CompletableFuture<String> requestBike(@RequestParam("bikeId") String bikeId,
                                                 @RequestParam(value = "renter", required = false) String renter) {
        return commandDispatcher.send(
                new RequestBikeCommand(bikeId, renter != null ? renter : bikeRentalDataGenerator.randomRenter()),
                String.class);
    }

    @PostMapping("/returnBike")
    public CompletableFuture<Void> returnBike(@RequestParam("bikeId") String bikeId) {
        return commandDispatcher.send(new ReturnBikeCommand(bikeId, bikeRentalDataGenerator.randomLocation()));
    }

    @GetMapping("findPayment")
    public Mono<String> getPaymentId(@RequestParam("reference") String paymentRef) {
        return queryDispatcher.subscriptionQuery(PaymentStatusNamedQueries.GET_PAYMENT_ID, paymentRef, String.class)
                              .filter(Objects::nonNull)
                              .next();
    }

    @GetMapping("pendingPayments")
    public CompletableFuture<List<PaymentStatus>> getPendingPayments() {
        return queryDispatcher.queryMany(PaymentStatusNamedQueries.GET_ALL_PAYMENTS,
                                         PaymentStatus.Status.PENDING, PaymentStatus.class);
    }

    @PostMapping("acceptPayment")
    public CompletableFuture<Void> acceptPayment(@RequestParam("id") String paymentId) {
        return commandDispatcher.send(new ConfirmPaymentCommand(paymentId));
    }

    @GetMapping(value = "watch", produces = "text/event-stream")
    public Flux<String> watchAll() {
        return queryDispatcher.subscriptionQueryMany(BikeStatusNamedQueries.FIND_ALL, null, BikeStatus.class)
                              .map(bs -> bs.getBikeId() + " -> " + bs.description());
    }

    @GetMapping(value = "watch/{bikeId}", produces = "text/event-stream")
    public Flux<String> watchBike(@PathVariable("bikeId") String bikeId) {
        return queryDispatcher.subscriptionQuery(BikeStatusNamedQueries.FIND_ONE, bikeId, BikeStatus.class)
                              .map(bs -> bs.getBikeId() + " -> " + bs.description());
    }

    @PostMapping(value = "/generateRentals")
    public Flux<String> generateData(@RequestParam(value = "bikeType") String bikeType,
                                     @RequestParam("loops") int loops,
                                     @RequestParam(value = "concurrency", defaultValue = "1") int concurrency,
                                     @RequestParam(value = "abandonPaymentFactor", defaultValue = "100") int abandonPaymentFactor,
                                     @RequestParam(value = "delay", defaultValue = "0") int delay) {
        return bikeRentalDataGenerator.generateRentals(bikeType, loops, concurrency, abandonPaymentFactor, delay);
    }

    @GetMapping("/bikes/{bikeId}")
    public CompletableFuture<BikeStatus> findStatus(@PathVariable("bikeId") String bikeId) {
        return queryDispatcher.query(BikeStatusNamedQueries.FIND_ONE, bikeId, BikeStatus.class);
    }
}
