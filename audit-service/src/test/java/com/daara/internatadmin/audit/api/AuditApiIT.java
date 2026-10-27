package com.daara.internatadmin.audit.api;

import com.daara.internatadmin.audit.AbstractIntegrationTest;
import com.daara.internatadmin.audit.domain.AuditLog;
import com.daara.internatadmin.audit.domain.AuditLogRepository;
import com.daara.internatadmin.common.support.TestJwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.daara.internatadmin.common.support.TestJwts.bearer;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditApiIT extends AbstractIntegrationTest {

    private static final String ADMIN = bearer(TestJwts.token(1, "admin", "ADMIN"));
    private static final String GESTIONNAIRE = bearer(TestJwts.token(5, "awa.diop", "GESTIONNAIRE"));

    @Autowired
    MockMvc mvc;

    @Autowired
    AuditLogRepository repository;

    private final LocalDateTime base = LocalDateTime.of(2026, 10, 1, 8, 0);

    @BeforeEach
    void seed() {
        repository.deleteAll();
        // 25 actions de l'admin sur des enfants, 12 du gestionnaire sur des paiements
        for (int i = 0; i < 25; i++) {
            repository.save(log(1L, "admin", "UPDATE", "Enfant", (long) (i % 3), i));
        }
        for (int i = 0; i < 12; i++) {
            repository.save(log(5L, "awa.diop", "CREATE", "PaiementEnfant", (long) i, 100 + i));
        }
    }

    @Test
    void historique_contratDePaginationAvecLes9ChampsAuPremierNiveau() throws Exception {
        mvc.perform(get("/api/audit").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(37))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.numberOfElements").value(20))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.empty").value(false));
    }

    @Test
    void historique_champsDUneTraceIdentiquesAuMonolithe() throws Exception {
        mvc.perform(get("/api/audit").header("Authorization", ADMIN))
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].userId").value(5))
                .andExpect(jsonPath("$.content[0].username").value("awa.diop"))
                .andExpect(jsonPath("$.content[0].action").value("CREATE"))
                .andExpect(jsonPath("$.content[0].entityType").value("PaiementEnfant"))
                .andExpect(jsonPath("$.content[0].entityId").value(11))
                .andExpect(jsonPath("$.content[0].details").value("details-111"))
                .andExpect(jsonPath("$.content[0].actionDate").value("2026-10-01T09:51:00"))
                .andExpect(jsonPath("$.content[0].eventId").doesNotExist());
    }

    @Test
    void historique_trieDuPlusRecentAuPlusAncien() throws Exception {
        mvc.perform(get("/api/audit").param("page", "1").header("Authorization", ADMIN))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.numberOfElements").value(17))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[16].details").value("details-0"));
    }

    @Test
    void historique_filtreParEntite() throws Exception {
        mvc.perform(get("/api/audit").param("entityType", "Enfant").param("entityId", "2")
                        .header("Authorization", ADMIN))
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.content[*].entityId", everyItem(is(2))));
    }

    @Test
    void historique_filtreParTypeSeul() throws Exception {
        mvc.perform(get("/api/audit").param("entityType", "PaiementEnfant").header("Authorization", ADMIN))
                .andExpect(jsonPath("$.totalElements").value(12));
    }

    @Test
    void historique_filtreParUtilisateur() throws Exception {
        mvc.perform(get("/api/audit").param("userId", "5").header("Authorization", ADMIN))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.content[*].userId", everyItem(is(5))));
    }

    @Test
    void historique_interditAuGestionnaire() throws Exception {
        mvc.perform(get("/api/audit").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isForbidden());
    }

    @Test
    void historique_sansJeton403_jetonExpire401() throws Exception {
        mvc.perform(get("/api/audit")).andExpect(status().isForbidden());
        String expired = TestJwts.token(1, "admin", "ADMIN", Instant.now().minusSeconds(3600));
        mvc.perform(get("/api/audit").header("Authorization", bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_TOKEN"));
    }

    @Test
    void activiteRecente_uniquementCelleDeLUtilisateurConnecte_10ParPage() throws Exception {
        mvc.perform(get("/api/audit/me").header("Authorization", GESTIONNAIRE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.content[*].username", everyItem(is("awa.diop"))))
                .andExpect(jsonPath("$.content[0].details").value("details-111"))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.empty").value(false))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.numberOfElements").value(10));
    }

    @Test
    void health_publicEtUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApi_decritLesDeuxEndpointsEtLeursReponses() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/audit'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/audit'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/audit'].get.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/audit/me'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/audit'].get.security[0].bearerAuth").exists());
    }

    private AuditLog log(Long userId, String username, String action, String entityType, Long entityId, int minutes) {
        return new AuditLog(UUID.randomUUID(), userId, username, action, entityType, entityId,
                "details-" + minutes, base.plusMinutes(minutes));
    }
}
