package com.daara.internatadmin.audit.domain;

import com.daara.internatadmin.common.audit.AuditEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneId.of("Africa/Dakar"));
    private final AuditService service = new AuditService(repository, clock);
    private final Pageable pageable = PageRequest.of(0, 20);

    @Test
    void enregistrement_copieFideleDeLEvenement() {
        AuditEvent event = new AuditEvent(UUID.randomUUID(), 5L, "awa.diop", "CANCEL", "Depense", 9L, "d",
                LocalDateTime.of(2026, 10, 2, 11, 30));

        service.record(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(event.eventId());
        assertThat(saved.getUserId()).isEqualTo(5L);
        assertThat(saved.getUsername()).isEqualTo("awa.diop");
        assertThat(saved.getAction()).isEqualTo("CANCEL");
        assertThat(saved.getEntityType()).isEqualTo("Depense");
        assertThat(saved.getEntityId()).isEqualTo(9L);
        assertThat(saved.getDetails()).isEqualTo("d");
        assertThat(saved.getActionDate()).isEqualTo(LocalDateTime.of(2026, 10, 2, 11, 30));
    }

    @Test
    void enregistrement_dateAbsente_dateCourante() {
        service.record(new AuditEvent(UUID.randomUUID(), 1L, "admin", "PRINT_CARD", "Enfant", null, null, null));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActionDate()).isEqualTo(LocalDateTime.of(2026, 10, 27, 10, 0));
        assertThat(captor.getValue().getEntityId()).isNull();
        assertThat(captor.getValue().getDetails()).isNull();
    }

    @Test
    void enregistrement_textesTronquesAuxLongueursDesColonnes() {
        service.record(new AuditEvent(UUID.randomUUID(), 1L, "u".repeat(60), "a".repeat(60), "e".repeat(60), 1L,
                "d".repeat(501), LocalDateTime.now(clock)));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).hasSize(50);
        assertThat(captor.getValue().getAction()).hasSize(50);
        assertThat(captor.getValue().getEntityType()).hasSize(50);
        assertThat(captor.getValue().getDetails()).hasSize(500);
    }

    @Test
    void enregistrement_detailDe500CaracteresConserveIntegralement() {
        service.record(new AuditEvent(UUID.randomUUID(), 1L, "admin", "UPDATE", "Enfant", 1L,
                "d".repeat(500), LocalDateTime.now(clock)));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).hasSize(500);
    }

    @Test
    void enregistrement_evenementDejaConnu_ignore() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(true);

        service.record(new AuditEvent(eventId, 1L, "admin", "CREATE", "Enfant", 1L, "d", LocalDateTime.now(clock)));

        verify(repository, never()).save(any());
    }

    @Test
    void enregistrement_evenementIncomplet_refuse() {
        assertThatThrownBy(() -> service.record(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(new AuditEvent(null, 1L, "a", "CREATE", "Enfant", 1L, "d", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(new AuditEvent(UUID.randomUUID(), null, "a", "CREATE", "E", 1L, "d", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(new AuditEvent(UUID.randomUUID(), 1L, " ", "CREATE", "E", 1L, "d", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(new AuditEvent(UUID.randomUUID(), 1L, "a", null, "E", 1L, "d", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(new AuditEvent(UUID.randomUUID(), 1L, "a", "CREATE", "", 1L, "d", null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void evenementIncomplet_leMessageDErreurNeContientPasLeDetail() {
        assertThatThrownBy(() -> service.record(new AuditEvent(UUID.randomUUID(), null, "a", "CREATE", "Enfant", 1L,
                "detail-confidentiel", null)))
                .hasMessageNotContaining("detail-confidentiel");
    }

    @Test
    void recherche_entiteEtIdentifiant_prioritaires() {
        service.search("Enfant", 3L, 5L, pageable);
        verify(repository).findByEntityTypeAndEntityId("Enfant", 3L, pageable);
    }

    @Test
    void recherche_typeSeul_avantUtilisateur() {
        service.search("Enfant", null, 5L, pageable);
        verify(repository).findByEntityType("Enfant", pageable);
    }

    @Test
    void recherche_utilisateur() {
        service.search(null, 3L, 5L, pageable);
        verify(repository).findByUserId(5L, pageable);
    }

    @Test
    void recherche_sansFiltre() {
        service.search(null, null, null, pageable);
        verify(repository).findAll(pageable);
    }
}
