package com.daara.internatadmin.common.security;

import com.daara.internatadmin.common.util.RsaKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Securite commune des services metier : verification du JWT RS256 (cle publique uniquement),
 * roles par @PreAuthorize, protection de /internal/**.
 *
 * <ul>
 *   <li>sans jeton : 403, comme le monolithe (Http403ForbiddenEntryPoint) ;</li>
 *   <li>jeton expire, mal forme ou mal signe : 401 INVALID_TOKEN ;</li>
 *   <li>role insuffisant : 403.</li>
 * </ul>
 * L'etat du compte (actif, changement de mot de passe) est controle par la gateway.
 * Desactivable par internatadmin.security.enabled=false (gateway).
 */
@AutoConfiguration(before = {SecurityAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
@ConditionalOnProperty(prefix = "internatadmin.security", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(InternatAdminSecurityProperties.class)
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerSecurityAutoConfiguration {

    static final List<String> ALWAYS_PUBLIC = List.of(
            "/error", "/health", "/actuator/health", "/actuator/health/**",
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml");

    @Bean
    JwtDecoder jwtDecoder(InternatAdminSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(RsaKeys.publicKeyFromBase64Pem(properties.jwtPublicKeyB64()))
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(properties.clockSkew())));
        return decoder;
    }

    @Bean
    SecurityFilterChain internatAdminSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                                         InternatAdminSecurityProperties properties,
                                                         ObjectMapper objectMapper) throws Exception {
        List<String> publicPaths = new ArrayList<>(ALWAYS_PUBLIC);
        publicPaths.addAll(properties.publicPaths());

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new Http403ForbiddenEntryPoint()))
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(new PublicPathsBearerTokenResolver(publicPaths))
                        .authenticationEntryPoint(new InvalidTokenEntryPoint(objectMapper))
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(new CurrentUserJwtConverter())))
                .addFilterAfter(new InternalApiKeyFilter(properties.internalApiKey(), objectMapper),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
