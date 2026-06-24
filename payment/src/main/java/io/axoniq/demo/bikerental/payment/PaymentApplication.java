package io.axoniq.demo.bikerental.payment;

import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus;
import io.axoniq.demo.bikerental.payment.eventhandling.ProjectionToken;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * The Payment application. Command handling and the read model are implemented directly against the
 * Axon Server gRPC API (see the {@code support} and {@code eventhandling} packages); Spring Boot
 * provides the web layer and JPA-backed read model storage.
 */
@EntityScan(basePackageClasses = {PaymentStatus.class, ProjectionToken.class})
@SpringBootApplication
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
