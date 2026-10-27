package com.daara.internatadmin.pilotage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration(proxyBeanMethods = false)
public class PilotageConfig {

    /** Mois courant des statistiques : fuseau de la JVM (TZ du conteneur), comme LocalDate.now() dans le monolithe. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean(destroyMethod = "close")
    ExecutorService pilotageExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ParallelCalls parallelCalls(ExecutorService pilotageExecutor, @Value("${internatadmin.pilotage.max-wait}") Duration maxWait) {
        return new ParallelCalls(pilotageExecutor, maxWait);
    }
}
