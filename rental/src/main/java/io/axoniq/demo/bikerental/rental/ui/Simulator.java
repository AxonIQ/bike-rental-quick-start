package io.axoniq.demo.bikerental.rental.ui;

import io.axoniq.demo.bikerental.coreapi.rental.CountOfBikesByTypeQuery;
import io.axoniq.demo.bikerental.coreapi.rental.RegisterBikeCommand;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
@Profile("simulator")
@EnableAsync
public class Simulator {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;
    private final BikeRentalDataGenerator bikeRentalDataGenerator;

    @Value("${inventory.size}")
    private int inventorySize;

    @Value("${inventory.bikeType}")
    private String inventoryBikeType;

    @Value("${rentalSimulation.bikeType}")
    private String rentalBikeType;

    @Value("${rentalSimulation.loops}")
    private int loops;

    @Value("${rentalSimulation.concurrency}")
    private int concurrency;

    @Value("${rentalSimulation.abandonPaymentFactor}")
    private int abandonPaymentFactor;

    @Value("${rentalSimulation.delayBetweenLoops}")
    private int delayBetweenLoops;

    Logger logger = LoggerFactory.getLogger(Simulator.class);

    public Simulator(CommandGateway commandGateway, QueryGateway queryGateway, BikeRentalDataGenerator bikeRentalDataGenerator) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
        this.bikeRentalDataGenerator = bikeRentalDataGenerator;
    }

    public void updateInventoryCreationConfiguration(int sizeOfBikeInventory, String bikeType) {
        this.inventorySize = sizeOfBikeInventory;
        this.inventoryBikeType = bikeType;
    }

    public void updateRentalGenerationConfiguration(String rentalBikeType, int loops, int concurrency, int abandonPaymentFactor, int delay ){
        this.rentalBikeType = rentalBikeType;
        this.loops = loops;
        this.concurrency = concurrency;
        this.abandonPaymentFactor = abandonPaymentFactor;
        this.delayBetweenLoops = delay;
    }

    @Scheduled(fixedRate = 25000, initialDelay = 5000)
    private void generateData() {
        try{
            this.generateBikes();
        } catch (Exception ex) {
            logger.error("error generating inventory", ex);
        }

        this.generateRentals();
    }

    public void generateBikes() throws ExecutionException, InterruptedException {

        var query = new CountOfBikesByTypeQuery(this.inventoryBikeType);
        long currentBikeCount = queryGateway.query(query,
                                                    ResponseTypes.instanceOf(long.class)).get();

        if (currentBikeCount < this.inventorySize) {
            for (int i = 0; i < 10; i++) {
                commandGateway.send(new RegisterBikeCommand(UUID.randomUUID().toString(), this.inventoryBikeType, this.bikeRentalDataGenerator.randomLocation()));
            }
        }
    }


    private void generateRentals() {
        this.bikeRentalDataGenerator.generateRentals(this.rentalBikeType,
                this.loops,
                this.concurrency,
                this.abandonPaymentFactor,
                this.delayBetweenLoops).subscribe(logger::info);
    }
}
