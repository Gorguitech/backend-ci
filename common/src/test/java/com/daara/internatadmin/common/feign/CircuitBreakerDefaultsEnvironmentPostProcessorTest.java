package com.daara.internatadmin.common.feign;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CircuitBreakerDefaultsEnvironmentPostProcessorTest {

    private static final String THREAD_POOL = "spring.cloud.circuitbreaker.resilience4j.disable-thread-pool";
    private static final String TIME_LIMITER = "spring.cloud.circuitbreaker.resilience4j.disable-time-limiter";

    private final CircuitBreakerDefaultsEnvironmentPostProcessor processor = new CircuitBreakerDefaultsEnvironmentPostProcessor();

    @Test
    void valeursParDefautAppliqueesATousLesServices() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(THREAD_POOL, Boolean.class)).isTrue();
        assertThat(environment.getProperty(TIME_LIMITER, Boolean.class)).isTrue();
    }

    @Test
    void valeurDefinieParUnServiceRestePrioritaire() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application.yml", Map.of(TIME_LIMITER, "false")));

        processor.postProcessEnvironment(environment, new SpringApplication());
        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(TIME_LIMITER, Boolean.class)).isFalse();
        assertThat(environment.getProperty(THREAD_POOL, Boolean.class)).isTrue();
        assertThat(environment.getPropertySources().stream()
                .filter(source -> source.getName().equals(CircuitBreakerDefaultsEnvironmentPostProcessor.SOURCE_NAME)))
                .as("source ajoutee une seule fois").hasSize(1);
    }

    @Test
    void declareDansSpringFactories() throws Exception {
        try (var in = getClass().getResourceAsStream("/META-INF/spring.factories")) {
            String factories = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(factories)
                    .contains(EnvironmentPostProcessor.class.getName())
                    .contains(CircuitBreakerDefaultsEnvironmentPostProcessor.class.getName());
        }
    }
}
