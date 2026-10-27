package com.daara.internatadmin.gateway;

import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.common.error.ServiceUnavailableException;
import com.daara.internatadmin.common.web.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.Arrays;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.setPath;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * Routes vers les services, evaluees dans l'ordre de declaration. Les statistiques des eleves et des employes
 * sont calculees par pilotage-service : leurs routes passent avant /api/enfants/** et /api/employes/**.
 * /internal/** n'est route nulle part (404).
 */
@Configuration(proxyBeanMethods = false)
public class GatewayRoutesConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayRoutesConfig.class);

    @Bean
    RouterFunction<ServerResponse> gatewayRoutes(GatewayProperties properties) {
        GatewayProperties.Services s = properties.services();
        return service("pilotage", s.pilotage(),
                "/api/enfants/stats", "/api/employes/stats", "/api/dashboard/**", "/api/notifications/**")
                .and(service("auth", s.auth(), "/api/auth/**", "/api/users/**", "/api/parametres/**"))
                .and(service("enfant", s.enfant(), "/api/enfants/**"))
                .and(service("personnel", s.personnel(), "/api/employes/**"))
                .and(service("paiement", s.paiement(), "/api/paiements-enfants/**", "/api/paiements-salaires/**"))
                .and(service("comptabilite", s.comptabilite(),
                        "/api/categories-depenses/**", "/api/depenses/**", "/api/autres-recettes/**", "/api/tresorerie/**"))
                .and(service("audit", s.audit(), "/api/audit/**"))
                .and(apiDocs("auth", s.auth()))
                .and(apiDocs("audit", s.audit()))
                .and(apiDocs("enfant", s.enfant()))
                .and(apiDocs("personnel", s.personnel()))
                .and(apiDocs("paiement", s.paiement()))
                .and(apiDocs("comptabilite", s.comptabilite()))
                .and(apiDocs("pilotage", s.pilotage()))
                .filter(propagateRequestId())
                .filter(downstreamFailureAs503());
    }

    private static RouterFunction<ServerResponse> service(String id, String uri, String... patterns) {
        RequestPredicate predicate = Arrays.stream(patterns)
                .map(pattern -> path(pattern))
                .reduce(RequestPredicate::or)
                .orElseThrow();
        return route(id).route(predicate, http()).before(uri(uri)).build();
    }

    /** Document OpenAPI d'un service, affiche par le Swagger UI de la gateway. */
    private static RouterFunction<ServerResponse> apiDocs(String id, String uri) {
        return route(id + "-api-docs")
                .route(GET("/api-docs/" + id), http())
                .before(uri(uri))
                .before(setPath("/v3/api-docs"))
                .build();
    }

    private static HandlerFilterFunction<ServerResponse, ServerResponse> propagateRequestId() {
        return (request, next) -> {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId == null) {
                return next.handle(request);
            }
            ServerRequest withId = ServerRequest.from(request)
                    .headers(headers -> headers.set(RequestIdFilter.HEADER, requestId))
                    .build();
            return next.handle(withId);
        };
    }

    // Service injoignable ou coupure pendant l'echange : 503, jamais 401 (le frontend deconnecterait l'utilisateur).
    private static HandlerFilterFunction<ServerResponse, ServerResponse> downstreamFailureAs503() {
        return (request, next) -> {
            try {
                return next.handle(request);
            } catch (Exception e) {
                log.warn("Service en aval injoignable pour {} {} : {}", request.method(), request.path(), e.toString());
                return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new ErrorResponse("SERVICE_UNAVAILABLE", ServiceUnavailableException.DEFAULT_MESSAGE));
            }
        };
    }
}
