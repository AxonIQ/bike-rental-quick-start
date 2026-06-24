package io.axoniq.demo.bikerental.rental.config;

import io.axoniq.axonserver.connector.AxonServerConnection;
import io.axoniq.axonserver.connector.AxonServerConnectionFactory;
import io.axoniq.axonserver.connector.command.CommandChannel;
import io.axoniq.axonserver.connector.event.EventChannel;
import io.axoniq.axonserver.connector.impl.ServerAddress;
import io.axoniq.axonserver.connector.query.QueryChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the raw connection to Axon Server and exposes its gRPC channels as beans.
 * This replaces the Axon Framework auto-configuration: there is no command bus, query bus,
 * event store abstraction or processor configuration here - just the bare channels.
 */
@Configuration
public class AxonServerConfig {

    @Bean(destroyMethod = "shutdown")
    public AxonServerConnectionFactory axonServerConnectionFactory(
            @Value("${spring.application.name:rental}") String componentName,
            @Value("${axon.axonserver.host:localhost}") String host,
            @Value("${axon.axonserver.port:8124}") int port) {
        return AxonServerConnectionFactory.forClient(componentName)
                                          .routingServers(new ServerAddress(host, port))
                                          .build();
    }

    @Bean(destroyMethod = "disconnect")
    public AxonServerConnection axonServerConnection(AxonServerConnectionFactory factory,
                                                     @Value("${axon.axonserver.context:default}") String context) {
        return factory.connect(context);
    }

    @Bean
    public CommandChannel commandChannel(AxonServerConnection connection) {
        return connection.commandChannel();
    }

    @Bean
    public QueryChannel queryChannel(AxonServerConnection connection) {
        return connection.queryChannel();
    }

    @Bean
    public EventChannel eventChannel(AxonServerConnection connection) {
        return connection.eventChannel();
    }
}
