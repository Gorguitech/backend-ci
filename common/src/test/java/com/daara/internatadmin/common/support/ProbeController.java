package com.daara.internatadmin.common.support;

import com.daara.internatadmin.common.error.AmountMismatchException;
import com.daara.internatadmin.common.error.DuplicateEntityException;
import com.daara.internatadmin.common.error.PartialPaymentException;
import com.daara.internatadmin.common.error.PaymentAlreadyExistsException;
import com.daara.internatadmin.common.error.ServiceUnavailableException;
import com.daara.internatadmin.common.security.CurrentUser;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Endpoints de test qui exercent la securite et la gestion d'erreurs du module. */
@RestController
public class ProbeController {

    public record ProbeRequest(@NotBlank String name) {
    }

    @GetMapping("/api/probe/me")
    public CurrentUser me(@AuthenticationPrincipal CurrentUser user) {
        return user;
    }

    @GetMapping("/api/probe/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
        return "admin";
    }

    @GetMapping("/api/public/ping")
    public String publicPing() {
        return "pong";
    }

    @GetMapping("/internal/probe")
    public String internal() {
        return "interne";
    }

    @PostMapping("/api/probe/validate")
    public String validate(@Valid @RequestBody ProbeRequest request) {
        return request.name();
    }

    @GetMapping("/api/probe/errors/{kind}")
    public String fail(@PathVariable String kind) {
        Request feignRequest = Request.create(Request.HttpMethod.GET, "http://enfant-service/internal/enfants/1",
                Map.of(), null, StandardCharsets.UTF_8, null);
        throw switch (kind) {
            case "bad-credentials" -> new BadCredentialsException("Identifiant ou mot de passe incorrect.");
            case "illegal-argument" -> new IllegalArgumentException("Enfant introuvable : 12");
            case "disabled" -> new DisabledException("desactive");
            case "duplicate" -> new DuplicateEntityException("Doublon possible");
            case "payment-exists" -> new PaymentAlreadyExistsException("Deja paye");
            case "amount-mismatch" -> new AmountMismatchException("Montant incorrect");
            case "partial" -> new PartialPaymentException("Paiement partiel");
            case "too-large" -> new MaxUploadSizeExceededException(2 * 1024 * 1024);
            case "unavailable" -> new ServiceUnavailableException();
            case "lock" -> new CannotAcquireLockException("lock_timeout");
            case "illegal-state" -> new IllegalStateException("Impossible de générer un matricule unique");
            case "feign-retryable" -> new RetryableException(-1, "Connection refused",
                    Request.HttpMethod.GET, (Long) null, feignRequest);
            case "no-fallback-illegal" -> new NoFallbackAvailableException("sans repli",
                    new IllegalArgumentException("Enfant introuvable : 99"));
            case "no-fallback-unavailable" -> new NoFallbackAvailableException("sans repli",
                    new ServiceUnavailableException());
            case "circuit-open" -> {
                CircuitBreaker breaker = CircuitBreaker.ofDefaults("enfant-service");
                breaker.transitionToOpenState();
                yield CallNotPermittedException.createCallNotPermittedException(breaker);
            }
            default -> new UnsupportedOperationException(kind);
        };
    }
}
