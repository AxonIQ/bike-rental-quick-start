package io.axoniq.demo.bikerental.rental;

import io.axoniq.demo.bikerental.coreapi.rental.BikeStatus;
import io.axoniq.demo.bikerental.rental.eventhandling.ProjectionToken;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The Rental application. It no longer uses the Axon Framework: command handling, the read model,
 * the payment saga and the event "processors" are all implemented directly against the Axon Server
 * gRPC API (see the {@code command}, {@code query}, {@code paymentsaga} and {@code eventhandling}
 * packages). Spring Boot still provides the web layer and JPA-backed read model storage.
 */
@EntityScan(basePackageClasses = {BikeStatus.class, ProjectionToken.class})
@SpringBootApplication
@EnableScheduling
public class RentalApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentalApplication.class, args);
    }
}
