package com.daara.internatadmin.common.error;

import com.daara.internatadmin.common.support.AbstractWebTest;
import com.daara.internatadmin.common.support.TestJwts;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static com.daara.internatadmin.common.support.TestJwts.bearer;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Statuts, codes et messages identiques a ceux du monolithe, plus les ajouts valides (503). */
class GlobalExceptionHandlerTest extends AbstractWebTest {

    private final String token = bearer(TestJwts.token(1, "admin", "ADMIN"));

    @ParameterizedTest(name = "{0} -> {1} {2}")
    @CsvSource(delimiter = '|', value = {
            "bad-credentials         | 400 | INVALID_CURRENT_PASSWORD      | Identifiant ou mot de passe incorrect.",
            "illegal-argument        | 400 | INVALID_REQUEST               | Enfant introuvable : 12",
            "disabled                | 403 | ACCOUNT_DISABLED              | Ce compte a été désactivé.",
            "duplicate               | 409 | POSSIBLE_DUPLICATE            | Doublon possible",
            "payment-exists          | 409 | PAYMENT_ALREADY_EXISTS        | Deja paye",
            "amount-mismatch         | 409 | AMOUNT_MISMATCH               | Montant incorrect",
            "partial                 | 409 | PARTIAL_PAYMENT_NOT_CONFIRMED | Paiement partiel",
            "too-large               | 413 | FILE_TOO_LARGE                | Le fichier dépasse la taille maximale autorisée.",
            "unavailable             | 503 | SERVICE_UNAVAILABLE           | Service temporairement indisponible. Veuillez réessayer.",
            "lock                    | 503 | SERVICE_UNAVAILABLE           | Service temporairement indisponible. Veuillez réessayer.",
            "feign-retryable         | 503 | SERVICE_UNAVAILABLE           | Service temporairement indisponible. Veuillez réessayer.",
            "no-fallback-unavailable | 503 | SERVICE_UNAVAILABLE           | Service temporairement indisponible. Veuillez réessayer.",
            "circuit-open            | 503 | SERVICE_UNAVAILABLE           | Service temporairement indisponible. Veuillez réessayer.",
            "no-fallback-illegal     | 400 | INVALID_REQUEST               | Enfant introuvable : 99"
    })
    void correspondanceExceptionStatutCode(String kind, int status, String error, String message) throws Exception {
        mvc.perform(get("/api/probe/errors/" + kind).header("Authorization", token))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void echecTechniqueImprevu_500EtJamais409() {
        // IllegalStateException n'a pas de handler : Spring renvoie 500 (MockMvc la propage telle quelle).
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        mvc.perform(get("/api/probe/errors/illegal-state").header("Authorization", token)))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void erreurDeValidation_corpsParDefautDeSpringSansMessage() throws Exception {
        mvc.perform(post("/api/probe/validate").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("VALIDATION_ERROR"))))
                .andExpect(content().string(not(containsString("\"message\""))));
    }
}
