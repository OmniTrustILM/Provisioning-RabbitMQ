package com.otilm.provisioning.rabbitmq;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    public RabbitMQContainer rabbitMQContainer(
        @Value("${spring.rabbitmq.username}") String username,
        @Value("${spring.rabbitmq.password}") String password) {
        return new RabbitMQContainer("rabbitmq:management")
            .withEnv("RABBITMQ_DEFAULT_USER", username)
            .withEnv("RABBITMQ_DEFAULT_PASS", password)
            .withEnv("RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS", "-rabbitmq_management load_definitions '/etc/rabbitmq/definitions.json'")
            .withCopyFileToContainer(MountableFile.forHostPath("rabbitmq/definitions.json"), "/etc/rabbitmq/definitions.json");
    }

    @Bean
    DynamicPropertyRegistrar rabbitProperties(RabbitMQContainer container) {
        return registry -> {
            registry.add("spring.rabbitmq.host", container::getHost);
            registry.add("spring.rabbitmq.port", container::getAmqpPort);
        };
    }
}
