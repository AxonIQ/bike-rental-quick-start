package io.axoniq.demo.bikerental.rental.ui;

import io.axoniq.demo.bikerental.coreapi.payment.ConfirmPaymentCommand;
import io.axoniq.demo.bikerental.coreapi.rental.*;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.queryhandling.SubscriptionQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
public class BikeRentalDataGenerator {
    private static final List<String> RENTERS = Arrays.asList("Allard", "Steven", "Josh", "David", "Marc", "Sara", "Milan", "Jeroen", "Marina", "Jeannot");
    private static final List<String> LOCATIONS = Arrays.asList("Amsterdam", "Paris", "Vilnius", "Barcelona", "London", "New York", "Toronto", "Berlin", "Milan", "Rome", "Belgrade");


    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    Logger logger = LoggerFactory.getLogger(BikeRentalDataGenerator.class);

    public BikeRentalDataGenerator(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }


    public String randomRenter() {
        return RENTERS.get(ThreadLocalRandom.current().nextInt(RENTERS.size()));
    }

    public String randomLocation() {
        return LOCATIONS.get(ThreadLocalRandom.current().nextInt(LOCATIONS.size()));
    }

    public CompletableFuture<Void> generateBikes(int bikeCount, String bikeType) {
        CompletableFuture<Void> all = CompletableFuture.completedFuture(null);
        for (int i = 0; i < bikeCount; i++) {
            all = CompletableFuture.allOf(all,
                    commandGateway.send(new RegisterBikeCommand(UUID.randomUUID().toString(), bikeType, randomLocation())));
        }
        return all;
    }


    public Flux<String> generateRentals(String bikeType,
                                int loops,
                                int concurrency,
                                int abandonPaymentFactor,
                                int delay) {

        return this.internalGenerateRentals(bikeType, loops, concurrency, abandonPaymentFactor, delay);

    }

    private Flux<String> internalGenerateRentals(String bikeType,
                                 int loops,
                                 int concurrency,
                                 int abandonPaymentFactor,
                                 int delay) {

        return Flux.range(0, loops)
                .flatMap(j -> executeRentalCycle(bikeType, randomRenter(), abandonPaymentFactor, delay)
                                .map(r -> "OK - Rented, Payed and Returned\n")
                                .onErrorResume(e -> Mono.just("Not ok: " + e.getMessage() + "\n")),
                        concurrency);

    }

    private Mono<String> executeRentalCycle(String bikeType, String renter, int abandonPaymentFactor, int delay) {
        CompletableFuture<String> result = selectRandomAvailableBike(bikeType)
                .thenCompose(bikeId -> commandGateway.send(new RequestBikeCommand(bikeId, renter))
                        .thenComposeAsync(paymentRef -> executePayment(bikeId,
                                        (String) paymentRef,
                                        abandonPaymentFactor),
                                CompletableFuture.delayedExecutor(randomDelay(
                                        delay), TimeUnit.MILLISECONDS))
                        .thenCompose(r -> whenBikeUnlocked(bikeId))
                        .thenComposeAsync(r -> commandGateway.send(new ReturnBikeCommand(
                                        bikeId,
                                        randomLocation())),
                                CompletableFuture.delayedExecutor(randomDelay(
                                        delay), TimeUnit.MILLISECONDS))
                        .thenApply(r -> bikeId));
        return Mono.fromFuture(result);
    }

    private CompletableFuture<String> selectRandomAvailableBike(String bikeType) {
        return queryGateway.query("findAvailable", bikeType, ResponseTypes.multipleInstancesOf(BikeStatus.class))
                .thenApply(this::pickRandom)
                .thenApply(BikeStatus::getBikeId);
    }

    private CompletableFuture<String> executePayment(String bikeId, String paymentRef, int abandonPaymentFactor) {
        if (abandonPaymentFactor > 0 && ThreadLocalRandom.current().nextInt(abandonPaymentFactor) == 0) {
            return CompletableFuture.failedFuture(new IllegalStateException("Customer refused to pay"));
        }
        SubscriptionQueryResult<String, String> queryResult = queryGateway.subscriptionQuery("getPaymentId",
                paymentRef,
                String.class,
                String.class);
        return queryResult.initialResult().concatWith(queryResult.updates())
                .filter(Objects::nonNull)
                .doOnNext(n -> queryResult.close())
                .next()
                .flatMap(paymentId -> Mono.fromFuture(commandGateway.send(new ConfirmPaymentCommand(paymentId))))
                .map(o -> bikeId)
                .toFuture();
    }
    private CompletableFuture<String> whenBikeUnlocked(String bikeId) {
        SubscriptionQueryResult<BikeStatus, BikeStatus> queryResult = queryGateway.subscriptionQuery(BikeStatusNamedQueries.FIND_ONE, bikeId, BikeStatus.class, BikeStatus.class);
        return queryResult.initialResult().concatWith(queryResult.updates())
                .any(status -> status.getStatus() == RentalStatus.RENTED)
                .map(s -> bikeId)
                .doOnNext(n -> queryResult.close())
                .toFuture();
    }
    private int randomDelay(int delay) {
        if (delay <= 0) {
            return 0;
        }
        return ThreadLocalRandom.current().nextInt(delay - (delay >> 2), delay + delay + (delay >> 2));
    }

    private <T> T pickRandom(List<T> source) {
        return source.get(ThreadLocalRandom.current().nextInt(source.size()));
    }
}
