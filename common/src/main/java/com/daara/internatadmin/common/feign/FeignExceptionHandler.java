package com.daara.internatadmin.common.feign;

import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.common.error.GlobalExceptionHandler;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs techniques des appels Feign (connexion refusee, delai depasse) : toujours 503.
 * Avec le circuit breaker, Spring Cloud enveloppe l'exception d'origine dans NoFallbackAvailableException :
 * on la deballe pour conserver le 400 INVALID_REQUEST renvoye par le service appele.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FeignExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FeignExceptionHandler.class);

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeign(FeignException ex) {
        log.warn("Appel inter-services en echec : {}", ex.toString());
        return GlobalExceptionHandler.serviceUnavailable();
    }

    @ExceptionHandler(NoFallbackAvailableException.class)
    public ResponseEntity<ErrorResponse> handleNoFallback(NoFallbackAvailableException ex) {
        if (ex.getCause() instanceof IllegalArgumentException cause) {
            return GlobalExceptionHandler.error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", cause.getMessage());
        }
        log.warn("Appel inter-services en echec : {}", String.valueOf(ex.getCause()));
        return GlobalExceptionHandler.serviceUnavailable();
    }
}
