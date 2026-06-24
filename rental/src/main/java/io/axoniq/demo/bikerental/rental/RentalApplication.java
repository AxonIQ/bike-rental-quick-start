package io.axoniq.demo.bikerental.rental;

import io.axoniq.demo.bikerental.coreapi.rental.BikeStatus;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The Rental application. It no longer uses the Axon Framework: command handling, the read model,
 * the payment saga and the event handlers are all implemented against the Axon Server
 * <em>Integration</em> HTTP API (see the {@code command}, {@code query}, {@code paymentsaga},
 * {@code handler} and {@code support} packages). Axon Server pushes commands, queries and events to
 * the {@code /axon/**} handler endpoints and serves as the event store over HTTP. Spring Boot still
 * provides the web layer and JPA-backed read model storage.
 */
@EntityScan(basePackageClasses = BikeStatus.class)
@SpringBootApplication
@EnableScheduling
public class RentalApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentalApplication.class, args);
    }
}
