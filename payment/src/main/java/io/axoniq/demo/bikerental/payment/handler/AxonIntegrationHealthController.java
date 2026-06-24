package io.axoniq.demo.bikerental.payment.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The health endpoint Axon Server polls (per the registered Integration endpoint) to decide whether
 * the payment service is available to receive commands, queries and events.
 */
@RestController
public class AxonIntegrationHealthController {

    @GetMapping("/axon/health")
    public ResponseEntity<Void> health() {
        return ResponseEntity.ok().build();
    }
}
