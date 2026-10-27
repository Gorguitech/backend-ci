package com.daara.internatadmin.common.security;

import com.daara.internatadmin.common.support.AbstractWebTest;
import com.daara.internatadmin.common.support.TestJwts;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.daara.internatadmin.common.support.TestJwts.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResourceServerSecurityTest extends AbstractWebTest {

    private static final String AUTH = "Authorization";

    @Test
    void sansJeton_403CommeLeMonolithe() throws Exception {
        mvc.perform(get("/api/probe/me"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
    }

    @Test
    void jetonValide_currentUserConstruitDepuisLesClaims() throws Exception {
        mvc.perform(get("/api/probe/me").header(AUTH, bearer(TestJwts.token(7, "awa.diop", "GESTIONNAIRE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.username").value("awa.diop"))
                .andExpect(jsonPath("$.role").value("GESTIONNAIRE"));
    }

    @Test
    void jetonExpireAuDelaDeLaTolerance_401InvalidToken() throws Exception {
        String token = TestJwts.token(1, "admin", "ADMIN", Instant.now().minusSeconds(70));
        mvc.perform(get("/api/probe/me").header(AUTH, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("Jeton invalide ou expiré."));
    }

    @Test
    void jetonExpireDansLaToleranceDe60s_accepte() throws Exception {
        String token = TestJwts.token(1, "admin", "ADMIN", Instant.now().minusSeconds(50));
        mvc.perform(get("/api/probe/me").header(AUTH, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void jetonSigneParUneAutreCle_401() throws Exception {
        String token = TestJwts.sign(TestJwts.OTHER_KEY,
                TestJwts.claims(1, "admin", "ADMIN", Instant.now().plusSeconds(600)).build());
        mvc.perform(get("/api/probe/me").header(AUTH, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void jetonMalForme_401() throws Exception {
        mvc.perform(get("/api/probe/me").header(AUTH, bearer("pas-un-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void jetonSansClaimRole_401() throws Exception {
        String token = TestJwts.sign(TestJwts.KEY,
                TestJwts.claims(1, "admin", "ADMIN", Instant.now().plusSeconds(600)).claim("role", null).build());
        mvc.perform(get("/api/probe/me").header(AUTH, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void roleInsuffisant_403() throws Exception {
        mvc.perform(get("/api/probe/admin").header(AUTH, bearer(TestJwts.token(2, "g", "GESTIONNAIRE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleAdmin_autorise() throws Exception {
        mvc.perform(get("/api/probe/admin").header(AUTH, bearer(TestJwts.token(1, "admin", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }

    @Test
    void cheminPublic_accessibleSansJeton() throws Exception {
        mvc.perform(get("/api/public/ping"))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    void cheminPublic_unJetonExpireEstIgnore() throws Exception {
        String expired = TestJwts.token(1, "admin", "ADMIN", Instant.now().minusSeconds(3600));
        mvc.perform(get("/api/public/ping").header(AUTH, bearer(expired)))
                .andExpect(status().isOk());
    }

    @Test
    void interne_sansCle_403() throws Exception {
        mvc.perform(get("/internal/probe").header(AUTH, bearer(TestJwts.token(1, "admin", "ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("INTERNAL_ACCESS_DENIED"));
    }

    @Test
    void interne_mauvaiseCle_403() throws Exception {
        mvc.perform(get("/internal/probe")
                        .header(AUTH, bearer(TestJwts.token(1, "admin", "ADMIN")))
                        .header(InternalApiKeyFilter.HEADER, "mauvaise-cle"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("INTERNAL_ACCESS_DENIED"));
    }

    @Test
    void interne_cleSansJeton_403() throws Exception {
        mvc.perform(get("/internal/probe").header(InternalApiKeyFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isForbidden());
    }

    @Test
    void interne_cleEtJeton_autorise() throws Exception {
        mvc.perform(get("/internal/probe")
                        .header(AUTH, bearer(TestJwts.token(1, "admin", "ADMIN")))
                        .header(InternalApiKeyFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(content().string("interne"));
    }

    @Test
    void documentationOpenApi_publiqueAvecSchemaBearer() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }
}
