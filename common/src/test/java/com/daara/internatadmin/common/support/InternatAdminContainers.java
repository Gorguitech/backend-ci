package com.daara.internatadmin.common.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Conteneurs PostgreSQL et RabbitMQ des tests d'integration, aux memes images que docker-compose.yml.
 * Partages par toutes les classes de test d'un service qui utilisent le meme contexte Spring.
 * Delai de demarrage elargi : sur une machine chargee (build complet, Jenkins), RabbitMQ peut depasser
 * la minute accordee par defaut.
 */
@TestConfiguration(proxyBeanMethods = false)
public class InternatAdminContainers {

    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withStartupTimeout(STARTUP_TIMEOUT);
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
                .withStartupTimeout(STARTUP_TIMEOUT);
    }
}
