package com.daara.internatadmin.common.feign;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * Valeurs par defaut de Spring Cloud CircuitBreaker (Resilience4j) pour tous les services :
 * <ul>
 *   <li>disable-thread-pool : l'appel protege s'execute dans le thread de la requete, ce qui conserve le MDC
 *       (X-Request-Id) et le contexte de la requete (jeton relaye) ;</li>
 *   <li>disable-time-limiter : pas de limite de temps Resilience4j, dont la valeur par defaut (1 s) couperait des
 *       appels encore dans les delais ; c'est le delai de lecture du client HTTP qui borne un appel trop lent.</li>
 * </ul>
 * Source ajoutee en derniere position : toute valeur definie par un service (application.yml, variable
 * d'environnement) reste prioritaire.
 */
public class CircuitBreakerDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String SOURCE_NAME = "internatadminCircuitBreakerDefaults";
    static final Map<String, Object> DEFAULTS = Map.of(
            "spring.cloud.circuitbreaker.resilience4j.disable-thread-pool", "true",
            "spring.cloud.circuitbreaker.resilience4j.disable-time-limiter", "true");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getPropertySources().contains(SOURCE_NAME)) {
            environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, DEFAULTS));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
