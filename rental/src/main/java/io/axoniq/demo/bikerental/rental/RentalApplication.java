package io.axoniq.demo.bikerental.rental;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.axoniq.demo.bikerental.coreapi.rental.BikeStatus;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.commandhandling.gateway.DefaultCommandGateway;
import org.axonframework.commandhandling.gateway.IntervalRetryScheduler;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.ConfigurationScopeAwareProvider;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.SimpleDeadlineManager;
import org.axonframework.eventhandling.tokenstore.jpa.TokenEntry;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.modelling.saga.repository.jpa.SagaEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@EntityScan(basePackageClasses = {BikeStatus.class, SagaEntry.class, TokenEntry.class})
@SpringBootApplication
@EnableScheduling
public class RentalApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentalApplication.class, args);
    }

    //configuring retry scheduler for command gateway.  This will handle retrying if a non-transient exception occurs
    //when attempting to send a command
    //https://docs.axoniq.io/reference-guide/axon-framework/axon-framework-commands/infrastructure#retryscheduler
    @Bean
    public CommandGateway commandGatewayWithRetry(CommandBus commandBus){

        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(5);
        IntervalRetryScheduler rs = IntervalRetryScheduler.builder().retryExecutor(scheduledExecutorService).maxRetryCount(5).retryInterval(1000).build();
        return DefaultCommandGateway.builder().commandBus(commandBus).retryScheduler(rs).build();
    }


    @Bean(destroyMethod = "")
    public DeadlineManager deadlineManager(TransactionManager transactionManager,
                                           Configuration config) {
        return SimpleDeadlineManager.builder()
                                    .transactionManager(transactionManager)
                                    .scopeAwareProvider(new ConfigurationScopeAwareProvider(config))
                                    .build();
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService workerExecutorService() {
        return Executors.newScheduledThreadPool(4);
    }


    @Autowired
    public void configureSerializers(ObjectMapper objectMapper) {
        objectMapper.activateDefaultTyping(objectMapper.getPolymorphicTypeValidator(),
                                           ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT);
    }

    @Bean
    public ConfigurerModule eventProcessingCustomizer() {
        return configurer -> configurer
                .eventProcessing()
                .registerPooledStreamingEventProcessor(
                        "PaymentSagaProcessor",
                        Configuration::eventStore,
                        (c, b) -> b.workerExecutor(workerExecutorService())
                                   .initialSegmentCount(2)
                                   .batchSize(100)
                                   .initialToken(StreamableMessageSource::createHeadToken)
                )
                .registerPooledStreamingEventProcessor(
                        "io.axoniq.demo.bikerental.rental.query",
                        Configuration::eventStore,
                        (c, b) -> b.workerExecutor(workerExecutorService())
                                   .batchSize(100)
                );
    }
}
