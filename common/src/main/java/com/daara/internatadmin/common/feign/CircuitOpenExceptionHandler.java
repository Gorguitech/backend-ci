package com.daara.internatadmin.common.feign;

import com.daara.internatadmin.common.error.ErrorResponse;
import com.daara.internatadmin.common.error.GlobalExceptionHandler;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Circuit ouvert : le service appele est considere indisponible, reponse immediate en 503. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CircuitOpenExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(CircuitOpenExceptionHandler.class);

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitOpen(CallNotPermittedException ex) {
        log.warn("Circuit ouvert : {}", ex.getMessage());
        return GlobalExceptionHandler.serviceUnavailable();
    }
}
