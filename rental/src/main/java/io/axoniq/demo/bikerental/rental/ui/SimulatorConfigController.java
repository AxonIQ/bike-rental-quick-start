package io.axoniq.demo.bikerental.rental.ui;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("simulator")
@RestController("/simulator")
public class SimulatorConfigController {

    private final Simulator simulator;

    public SimulatorConfigController(Simulator simulator) {
        this.simulator = simulator;
    }

    @PostMapping("/inventoryGenerationConfig")
    public void updateInventoryGeneratorConfig(@RequestParam(value = "size") Integer size,
                                               @RequestParam(value = "bikeType") String bikeType) {
        this.simulator.updateInventoryCreationConfiguration(size, bikeType);
    }

    @PostMapping("/rentalGenerationConfig")
    public void updateRentalGeneratorConfig(
            @RequestParam(value = "rentalBikeType") String rentalBikeType,
            @RequestParam(value = "loops") Integer loops,
            @RequestParam(value = "concurrency", defaultValue = "1") int concurrency,
            @RequestParam(value = "abandonPaymentFactor", defaultValue = "100") int abandonPaymentFactor,
            @RequestParam(value = "delay", defaultValue = "0")int delay) {
        this.simulator.updateRentalGenerationConfiguration(rentalBikeType, loops,concurrency, abandonPaymentFactor, delay);
    }
}
