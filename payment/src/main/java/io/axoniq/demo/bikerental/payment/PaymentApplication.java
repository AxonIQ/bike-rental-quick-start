package io.axoniq.demo.bikerental.payment;

import io.axoniq.demo.bikerental.coreapi.payment.PaymentStatus;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

/**
 * The Payment application. Command handling and the read model are implemented against the Axon
 * Server <em>Integration</em> HTTP API (see the {@code support} and {@code handler} packages):
 * Axon Server pushes commands, queries and events to the {@code /axon/**} endpoints and serves as the
 * event store over HTTP. Spring Boot provides the web layer and JPA-backed read model storage.
 */
@EntityScan(basePackageClasses = PaymentStatus.class)
@SpringBootApplication
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
