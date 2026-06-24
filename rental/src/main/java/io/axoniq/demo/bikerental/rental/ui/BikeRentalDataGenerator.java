package io.axoniq.demo.bikerental.rental.ui;

import io.axoniq.demo.bikerental.coreapi.payment.ConfirmPaymentCommand;
import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatusNamedQueries;
import io.axoniq.demo.bikerental.coreapi.rental.BikeStatus;
import io.axoniq.demo.bikerental.coreapi.rental.BikeStatusNamedQueries;
import io.axoniq.demo.bikerental.coreapi.rental.RegisterBikeCommand;
import io.axoniq.demo.bikerental.coreapi.rental.RentalStatus;
import io.axoniq.demo.bikerental.coreapi.rental.RequestBikeCommand;
import io.axoniq.demo.bikerental.coreapi.rental.ReturnBikeCommand;
import io.axoniq.demo.bikerental.rental.support.CommandDispatcher;
import io.axoniq.demo.bikerental.rental.support.QueryDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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

    private final CommandDispatcher commandDispatcher;
    private final QueryDispatcher queryDispatcher;

    Logger logger = LoggerFactory.getLogger(BikeRentalDataGenerator.class);

    public BikeRentalDataGenerator(CommandDispatcher commandDispatcher, QueryDispatcher queryDispatcher) {
        this.commandDispatcher = commandDispatcher;
        this.queryDispatcher = queryDispatcher;
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
                    commandDispatcher.send(new RegisterBikeCommand(UUID.randomUUID().toString(), bikeType, randomLocation())));
        }
        return all;
    }

    public Flux<String> generateRentals(String bikeType, int loops, int concurrency, int abandonPaymentFactor, int delay) {
        return Flux.range(0, loops)
                   .flatMap(j -> executeRentalCycle(bikeType, randomRenter(), abandonPaymentFactor, delay)
                                   .map(r -> "OK - Rented, Payed and Returned\n")
                                   .onErrorResume(e -> Mono.just("Not ok: " + e.getMessage() + "\n")),
                            concurrency);
    }

    private Mono<String> executeRentalCycle(String bikeType, String renter, int abandonPaymentFactor, int delay) {
        CompletableFuture<String> result = selectRandomAvailableBike(bikeType)
                .thenCompose(bikeId -> commandDispatcher.send(new RequestBikeCommand(bikeId, renter), String.class)
                        .thenComposeAsync(paymentRef -> executePayment(bikeId, paymentRef, abandonPaymentFactor),
                                CompletableFuture.delayedExecutor(randomDelay(delay), TimeUnit.MILLISECONDS))
                        .thenCompose(r -> whenBikeUnlocked(bikeId))
                        .thenComposeAsync(r -> commandDispatcher.send(new ReturnBikeCommand(bikeId, randomLocation())),
                                CompletableFuture.delayedExecutor(randomDelay(delay), TimeUnit.MILLISECONDS))
                        .thenApply(r -> bikeId));
        return Mono.fromFuture(result);
    }

    private CompletableFuture<String> selectRandomAvailableBike(String bikeType) {
        return queryDispatcher.queryMany(BikeStatusNamedQueries.FIND_AVAILABLE, bikeType, BikeStatus.class)
                              .thenApply(this::pickRandom)
                              .thenApply(BikeStatus::getBikeId);
    }

    private CompletableFuture<String> executePayment(String bikeId, String paymentRef, int abandonPaymentFactor) {
        if (abandonPaymentFactor > 0 && ThreadLocalRandom.current().nextInt(abandonPaymentFactor) == 0) {
            return CompletableFuture.failedFuture(new IllegalStateException("Customer refused to pay"));
        }
        return queryDispatcher.subscriptionQuery(PaymentStatusNamedQueries.GET_PAYMENT_ID, paymentRef, String.class)
                              .filter(Objects::nonNull)
                              .next()
                              .flatMap(paymentId -> Mono.fromFuture(commandDispatcher.send(new ConfirmPaymentCommand(paymentId))))
                              .map(o -> bikeId)
                              .toFuture();
    }

    private CompletableFuture<String> whenBikeUnlocked(String bikeId) {
        return queryDispatcher.subscriptionQuery(BikeStatusNamedQueries.FIND_ONE, bikeId, BikeStatus.class)
                              .any(status -> status.getStatus() == RentalStatus.RENTED)
                              .map(s -> bikeId)
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
