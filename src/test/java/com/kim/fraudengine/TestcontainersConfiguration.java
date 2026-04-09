package com.kim.fraudengine;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    private static final DockerImageName KAFKA_IMAGE =
            DockerImageName.parse("apache/kafka-native:3.8.0");
    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse("postgres:16-alpine");

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(KAFKA_IMAGE);
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE);
    }
}
