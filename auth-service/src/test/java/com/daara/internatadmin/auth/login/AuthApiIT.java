package com.daara.internatadmin.auth.login;

import com.daara.internatadmin.auth.AbstractIntegrationTest;
import com.daara.internatadmin.common.support.TestJwts;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiIT extends AbstractIntegrationTest {

    @Test
    void premiereConnexionDeLAdmin_reponseEtJetonConformesAuFrontend() throws Exception {
        JsonNode response = login("admin", ADMIN_INITIAL_PASSWORD);

        assertThat(response.get("username").asText()).isEqualTo("admin");
        assertThat(response.get("fullName").asText()).isEqualTo("Admin System");
        assertThat(response.get("role").asText()).isEqualTo("ADMIN");
        assertThat(response.get("mustChangePassword").asBoolean()).isTrue();

        // decodage comme le fait le frontend : atob sur la partie payload
        String[] parts = response.get("token").asText().split("\\.");
        JsonNode header = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        JsonNode payload = objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        assertThat(header.get("alg").asText()).isEqualTo("RS256");
        assertThat(fieldNames(payload)).containsExactlyInAnyOrder("sub", "userId", "role", "mustChangePassword", "iat", "exp");
        assertThat(payload.get("sub").asText()).isEqualTo("admin");
        assertThat(payload.get("userId").isIntegralNumber()).isTrue();
        assertThat(payload.get("userId").asLong()).isEqualTo(adminId());
        assertThat(payload.get("role").asText()).isEqualTo("ADMIN");
        assertThat(payload.get("mustChangePassword").isBoolean()).isTrue();
        assertThat(payload.get("exp").isIntegralNumber()).isTrue();
        assertThat(payload.get("exp").asLong() - payload.get("iat").asLong()).isEqualTo(480 * 60);
        assertThat(payload.get("exp").asLong()).isBetween(Instant.now().getEpochSecond(), Instant.now().getEpochSecond() + 480 * 60 + 5);
    }

    @Test
    void mauvaisMotDePasseOuUtilisateurInconnu_memeErreurQueLeMonolithe() throws Exception {
        for (String[] credentials : new String[][]{{"admin", "faux-mot-de-passe"}, {"inconnu", "x"}}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new LoginBody(credentials[0], credentials[1]))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_CURRENT_PASSWORD"))
                    .andExpect(jsonPath("$.message").value("Identifiant ou mot de passe incorrect."));
        }
    }

    @Test
    void compteDesactive_403AccountDisabled() throws Exception {
        String admin = adminBearer();
        JsonNode created = json(mvc.perform(post("/api/users").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Awa\",\"lastName\":\"Diop\"}")).andReturn());
        jdbc.update("UPDATE users SET active = FALSE WHERE username = 'awa.diop'");

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginBody("awa.diop", created.get("temporaryPassword").asText()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"))
                .andExpect(jsonPath("$.message").value("Ce compte a été désactivé."));
    }

    @Test
    void champsVides_400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ancienJetonExpireEnvoyeAuLogin_ignore() throws Exception {
        String expired = TestJwts.token(1, "admin", "ADMIN", Instant.now().minusSeconds(3600));
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer " + expired)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginBody("admin", ADMIN_INITIAL_PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    void parcoursDePremiereConnexion_changementPuisCodeDeRecuperation() throws Exception {
        String token = adminBearer();

        JsonNode changed = json(mvc.perform(put("/api/users/me/password").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + ADMIN_INITIAL_PASSWORD + "\",\"newPassword\":\"Nouveau-Mdp-2026\"}"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(changed.get("message").asText())
                .isEqualTo("Mot de passe changé. Notez votre code de récupération, il ne sera plus jamais affiché.");
        assertThat(changed.get("recoveryCode").asText()).hasSize(10);
        assertThat(fieldNames(changed)).as("aucun nouveau jeton").containsExactlyInAnyOrder("message", "recoveryCode");

        JsonNode relogin = login("admin", "Nouveau-Mdp-2026");
        assertThat(relogin.get("mustChangePassword").asBoolean()).isFalse();

        JsonNode second = json(mvc.perform(put("/api/users/me/password").header("Authorization", bearer(relogin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Nouveau-Mdp-2026\",\"newPassword\":\"Encore-Nouveau-2026\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(second.get("message").asText()).isEqualTo("Mot de passe changé avec succès.");
        assertThat(second.get("recoveryCode").isNull()).isTrue();
    }

    @Test
    void changementDeMotDePasse_erreurs() throws Exception {
        String token = adminBearer();
        mvc.perform(put("/api/users/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"faux\",\"newPassword\":\"Nouveau-Mdp-2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CURRENT_PASSWORD"))
                .andExpect(jsonPath("$.message").value("Mot de passe actuel incorrect"));
        mvc.perform(put("/api/users/me/password").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + ADMIN_INITIAL_PASSWORD + "\",\"newPassword\":\"court\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void codeDeRecuperation_tousLesCas() throws Exception {
        // le code n'existe qu'apres le premier changement de mot de passe de l'admin
        String code = json(mvc.perform(put("/api/users/me/password").header("Authorization", adminBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + ADMIN_INITIAL_PASSWORD + "\",\"newPassword\":\"Oublie-2026\"}"))
                .andReturn()).get("recoveryCode").asText();

        reset("inconnu", "X", "Nouveau-2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Utilisateur introuvable"));
        reset("admin", "MAUVAIS", "Nouveau-2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CURRENT_PASSWORD"))
                .andExpect(jsonPath("$.message").value("Code de récupération incorrect"));

        JsonNode ok = json(reset("admin", code, "Retrouve-2026").andExpect(status().isOk()).andReturn());
        assertThat(ok.get("message").asText()).isEqualTo(
                "Mot de passe réinitialisé. Notez votre nouveau code de récupération, il ne sera plus jamais affiché.");
        assertThat(ok.get("newRecoveryCode").asText()).hasSize(10).isNotEqualTo(code);
        assertThat(login("admin", "Retrouve-2026").get("mustChangePassword").asBoolean()).isFalse();

        reset("admin", code, "Autre-2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Code de récupération incorrect"));
    }

    @Test
    void codeDeRecuperation_compteSansCode() throws Exception {
        mvc.perform(post("/api/users").header("Authorization", adminBearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Awa\",\"lastName\":\"Diop\"}"));
        reset("awa.diop", "X", "Nouveau-2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Aucun code de récupération configuré pour ce compte"));
    }

    private org.springframework.test.web.servlet.ResultActions reset(String username, String code, String newPassword) throws Exception {
        return mvc.perform(post("/api/auth/reset-with-recovery-code").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "recoveryCode", code, "newPassword", newPassword))));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        Iterator<String> it = node.fieldNames();
        it.forEachRemaining(names::add);
        return names;
    }
}
