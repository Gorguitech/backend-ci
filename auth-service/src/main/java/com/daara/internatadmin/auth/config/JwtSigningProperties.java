package com.daara.internatadmin.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param privateKeyB64     cle privee RS256 (PKCS#8, PEM encode en base64), connue du seul auth-service
 * @param expirationMinutes duree de validite des jetons (480 minutes dans le monolithe)
 */
@Validated
@ConfigurationProperties(prefix = "internatadmin.jwt")
public record JwtSigningProperties(@NotBlank String privateKeyB64, @Positive long expirationMinutes) {

    public JwtSigningProperties {
        if (privateKeyB64 != null && privateKeyB64.startsWith("${")) {
            throw new IllegalStateException("internatadmin.jwt.private-key-b64 : variable d'environnement non definie");
        }
    }
}
