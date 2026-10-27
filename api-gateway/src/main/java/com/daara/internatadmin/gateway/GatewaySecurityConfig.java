package com.daara.internatadmin.gateway;

import com.daara.internatadmin.common.security.InternatAdminSecurityProperties;
import com.daara.internatadmin.common.security.RsaJwtDecoders;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Spring Security ne sert ici qu'au CORS (requetes preliminaires traitees avant tout controle).
 * Le controle d'acces est fait par {@link AccountAccessFilter}, place juste apres la chaine Spring Security.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternatAdminSecurityProperties.class)
@EnableWebSecurity
public class GatewaySecurityConfig {

    @Bean
    SecurityFilterChain gatewaySecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    // Memes regles que le monolithe, origines configurables par CORS_ALLOWED_ORIGINS.
    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.corsAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    JwtDecoder jwtDecoder(InternatAdminSecurityProperties properties) {
        return RsaJwtDecoders.create(properties);
    }

    @Bean
    AccountStateClient accountStateClient(GatewayProperties properties, Resilience4JCircuitBreakerFactory circuitBreakerFactory,
                                          InternatAdminSecurityProperties security) {
        Duration timeout = properties.accountStateTimeout();

        // Configure ici, juste avant create() : un Customizer serait applique trop tard et le circuit
        // garderait la configuration par defaut (delai de 1 s).
        circuitBreakerFactory.configure(builder -> builder
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .build())
                // filet de securite seulement : le delai effectif est celui du client HTTP (account-state-timeout).
                // Une marge large evite un faux 503 sur la toute premiere requete, qui paie le chargement des classes.
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(timeout.plusSeconds(3))
                        .build()), AccountStateClient.BREAKER_ID);
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        requestFactory.setReadTimeout(timeout);

        RestClient restClient = RestClient.builder()
                .baseUrl(properties.services().auth())
                .requestFactory(requestFactory)
                .build();
        return new AccountStateClient(restClient, circuitBreakerFactory.create(AccountStateClient.BREAKER_ID),
                security.internalApiKey());
    }

    @Bean
    ApplicationRunner accountStateClientWarmUp(AccountStateClient client) {
        return args -> client.warmUp();
    }

    @Bean
    FilterRegistrationBean<AccountAccessFilter> accountAccessFilter(JwtDecoder jwtDecoder, AccountStateClient client,
                                                                    ObjectMapper objectMapper) {
        FilterRegistrationBean<AccountAccessFilter> registration =
                new FilterRegistrationBean<>(new AccountAccessFilter(jwtDecoder, client, objectMapper));
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 1);
        return registration;
    }
}
