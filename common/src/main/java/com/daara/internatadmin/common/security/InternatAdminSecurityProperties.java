package com.daara.internatadmin.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Configuration de securite d'un service. Les secrets n'ont aucune valeur par defaut :
 * un service demarre sans eux echoue au demarrage.
 *
 * @param jwtPublicKeyB64 cle publique RS256, PEM encode en base64 (JWT_PUBLIC_KEY_B64)
 * @param clockSkew       tolerance d'horloge pour exp/nbf
 * @param internalApiKey  secret partage exige sur /internal/** (INTERNAL_API_KEY)
 * @param publicPaths     chemins accessibles sans jeton, en plus de /error, /health et Swagger
 */
@Validated
@ConfigurationProperties(prefix = "internatadmin.security")
public record InternatAdminSecurityProperties(
        @NotBlank String jwtPublicKeyB64,
        @NotNull Duration clockSkew,
        @NotBlank String internalApiKey,
        List<String> publicPaths
) {

    public InternatAdminSecurityProperties {
        if (clockSkew == null) {
            clockSkew = Duration.ofSeconds(60);
        }
        publicPaths = (publicPaths == null) ? List.of() : List.copyOf(publicPaths);
    }
}
