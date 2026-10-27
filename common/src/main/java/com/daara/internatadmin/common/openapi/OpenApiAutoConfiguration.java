package com.daara.internatadmin.common.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Description OpenAPI de base de chaque service et schema de securite bearerAuth,
 * reference par @SecurityRequirement(name = "bearerAuth") sur les controleurs (comme dans le monolithe).
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class OpenApiAutoConfiguration {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    @ConditionalOnMissingBean
    OpenAPI internatAdminOpenApi(@Value("${spring.application.name:internatadmin}") String applicationName) {
        return new OpenAPI()
                .info(new Info().title("InternatAdmin - " + applicationName).version("1.0"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
