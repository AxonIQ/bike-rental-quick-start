package io.axoniq.demo.bikerental.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.config.Configuration;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventhandling.tokenstore.TokenStore;
import org.axonframework.extensions.mongo.DefaultMongoTemplate;
import org.axonframework.extensions.mongo.eventsourcing.tokenstore.MongoTokenStore;
import org.axonframework.serialization.Serializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@SpringBootApplication
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService workerExecutorService() {
        return Executors.newScheduledThreadPool(4);
    }

    @Autowired
    public void configureSerializers(ObjectMapper objectMapper) {
        objectMapper.activateDefaultTyping(objectMapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT);
    }

    @Bean
    public ConfigurerModule eventProcessingCustomizer() {
        return configurer -> configurer
                .eventProcessing()
                .registerPooledStreamingEventProcessor(
                        "io.axoniq.demo.bikerental.payment",
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
}
