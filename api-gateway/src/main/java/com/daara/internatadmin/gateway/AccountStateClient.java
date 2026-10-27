package com.daara.internatadmin.gateway;

import com.daara.internatadmin.common.error.ServiceUnavailableException;
import com.daara.internatadmin.common.security.InternalApiKeyFilter;
import com.daara.internatadmin.common.web.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Lecture de l'etat du compte aupres d'auth-service, a chaque requete et sans cache : c'est ce qui garantit
 * le blocage immediat d'un compte desactive, meme avec un jeton encore valide.
 * Toute defaillance (delai depasse, erreur, circuit ouvert) se traduit par ServiceUnavailableException (503).
 */
public class AccountStateClient {

    public static final String BREAKER_ID = "auth-account-state";

    private static final Logger log = LoggerFactory.getLogger(AccountStateClient.class);

    /** Etat renvoye par GET /internal/users/{id}/state. */
    public record AccountState(Long id, boolean active, boolean mustChangePassword, String role) {
    }

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final String internalApiKey;

    public AccountStateClient(RestClient restClient, CircuitBreaker circuitBreaker, String internalApiKey) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
        this.internalApiKey = internalApiKey;
    }

    /**
     * Premier appel au demarrage (best effort) : il charge le client HTTP, pour que la premiere requete
     * d'un utilisateur ne depasse pas le delai court et ne recoive pas un 503 injustifie.
     */
    public void warmUp() {
        try {
            restClient.get().uri("/health").retrieve().toBodilessEntity();
        } catch (RuntimeException e) {
            log.info("Prechauffage du client auth-service sans reponse : {}", e.toString());
        }
    }

    /**
     * @return l'etat du compte, ou vide si auth-service ne connait pas l'utilisateur (400) ;
     *         un compte inconnu n'est pas une panne et ne doit pas ouvrir le circuit
     */
    public Optional<AccountState> fetch(long userId, String authorization, String requestId) {
        return circuitBreaker.run(() -> {
            try {
                return Optional.ofNullable(restClient.get()
                        .uri("/internal/users/{id}/state", userId)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .header(InternalApiKeyFilter.HEADER, internalApiKey)
                        .header(RequestIdFilter.HEADER, requestId)
                        .retrieve()
                        .body(AccountState.class));
            } catch (HttpClientErrorException.BadRequest e) {
                return Optional.empty();
            }
        }, error -> {
            log.warn("Etat du compte {} illisible : {}", userId, error.toString());
            throw new ServiceUnavailableException(error);
        });
    }
}
