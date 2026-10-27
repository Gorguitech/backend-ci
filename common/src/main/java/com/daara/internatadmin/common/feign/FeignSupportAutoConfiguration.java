package com.daara.internatadmin.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;

/**
 * Configuration par defaut de tous les clients Feign d'un service : relais du JWT, cle interne,
 * traduction des erreurs, nouvelle tentative sur erreur reseau et circuit breaker.
 * Les delais de connexion et de lecture se reglent dans l'application.yml du service
 * (spring.cloud.openfeign.client.config.default).
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
@Import(FeignExceptionHandler.class)
public class FeignSupportAutoConfiguration {

    @Bean
    RequestInterceptor feignRelayInterceptor(@Value("${internatadmin.security.internal-api-key}") String internalApiKey) {
        return new FeignRelayInterceptor(internalApiKey);
    }

    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    ErrorDecoder internalErrorDecoder(ObjectMapper objectMapper) {
        return new InternalErrorDecoder(objectMapper);
    }

    /**
     * Une seule nouvelle tentative, sur erreur reseau uniquement (connexion refusee ou coupee).
     * Sans risque : les appels inter-services sont tous des GET en lecture seule.
     */
    @Bean
    @ConditionalOnMissingBean(Retryer.class)
    Retryer feignRetryer() {
        return new Retryer.Default(100, 500, 2);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.github.resilience4j.circuitbreaker.CallNotPermittedException")
    @Import(CircuitOpenExceptionHandler.class)
    static class CircuitBreakerSupport {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Resilience4JCircuitBreakerFactory.class)
    static class Resilience4jDefaults {

        /**
         * Circuit de chaque client Feign. IllegalArgumentException est ignoree : un 400 INVALID_REQUEST
         * (par exemple « Enfant introuvable ») est une erreur de l'utilisateur, pas une panne, et ne doit pas
         * ouvrir le circuit.
         * <p>
         * La limite de temps de Resilience4j n'est pas reglee ici : Spring Cloud CircuitBreaker 3.3 la lit dans
         * le TimeLimiterRegistry et ignore celle de ce builder. Les services la desactivent
         * (spring.cloud.circuitbreaker.resilience4j.disable-time-limiter et disable-thread-pool) : l'appel reste
         * dans le thread de la requete (MDC et X-Request-Id conserves) et c'est le delai de lecture HTTP de Feign
         * qui coupe un appel trop lent.
         */
        @Bean
        Customizer<Resilience4JCircuitBreakerFactory> feignCircuitBreakerDefaults() {
            return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .circuitBreakerConfig(CircuitBreakerConfig.custom()
                            .slidingWindowSize(20)
                            .minimumNumberOfCalls(10)
                            .failureRateThreshold(50)
                            .waitDurationInOpenState(Duration.ofSeconds(10))
                            .ignoreExceptions(IllegalArgumentException.class)
                            .build())
                    .build());
        }
    }
}
