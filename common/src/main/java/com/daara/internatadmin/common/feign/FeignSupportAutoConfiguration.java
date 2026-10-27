package com.daara.internatadmin.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration par defaut de tous les clients Feign d'un service : relais du JWT, cle interne,
 * traduction des erreurs. Les delais et le circuit breaker se reglent dans l'application.yml du service.
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

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.github.resilience4j.circuitbreaker.CallNotPermittedException")
    @Import(CircuitOpenExceptionHandler.class)
    static class CircuitBreakerSupport {
    }
}
