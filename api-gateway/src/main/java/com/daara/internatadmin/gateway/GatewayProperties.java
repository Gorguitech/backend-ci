package com.daara.internatadmin.gateway;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * @param corsAllowedOrigins  origines du frontend (CORS_ALLOWED_ORIGINS, separees par des virgules)
 * @param accountStateTimeout delai maximal de lecture de l'etat du compte aupres d'auth-service
 * @param services            URL internes des services (reseau Docker)
 */
@Validated
@ConfigurationProperties(prefix = "internatadmin.gateway")
public record GatewayProperties(
        @NotEmpty List<String> corsAllowedOrigins,
        @NotNull Duration accountStateTimeout,
        @Valid @NotNull Services services
) {

    public record Services(
            @NotBlank String auth,
            @NotBlank String enfant,
            @NotBlank String personnel,
            @NotBlank String paiement,
            @NotBlank String comptabilite,
            @NotBlank String pilotage,
            @NotBlank String audit
    ) {
    }
}
