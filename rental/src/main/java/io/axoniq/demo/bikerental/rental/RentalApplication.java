package io.axoniq.demo.bikerental.rental;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.config.Configuration;
import org.axonframework.config.ConfigurationScopeAwareProvider;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.deadline.DeadlineManager;
import org.axonframework.deadline.SimpleDeadlineManager;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.extensions.mongo.DefaultMongoTemplate;
import org.axonframework.extensions.mongo.eventhandling.saga.repository.MongoSagaStore;
import org.axonframework.extensions.mongo.eventsourcing.tokenstore.MongoTokenStore;
import org.axonframework.messaging.StreamableMessageSource;
import org.axonframework.modelling.saga.repository.SagaStore;
import org.axonframework.serialization.Serializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@SpringBootApplication
@EnableScheduling
public class RentalApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentalApplication.class, args);
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


    @Bean
    public DefaultMongoTemplate axonMongoTemplate(MongoTemplate mongoTemplate) {
        return DefaultMongoTemplate.builder()
                .mongoDatabase(mongoTemplate.getMongoDatabaseFactory().getMongoDatabase())
                .build();
    }

    @Bean
    public TokenStore mongoTokenStore(DefaultMongoTemplate axonMongoTemplate, Serializer serializer) {

        return MongoTokenStore.builder()
                .mongoTemplate(axonMongoTemplate)
                .serializer(serializer)
                .build();
    }

    @Bean
    public SagaStore mongoSagaStore(DefaultMongoTemplate axonMongoTemplate, Serializer serializer) {

        return MongoSagaStore.builder()
                .mongoTemplate(axonMongoTemplate)
                .serializer(serializer)
                .build();
    }
}
