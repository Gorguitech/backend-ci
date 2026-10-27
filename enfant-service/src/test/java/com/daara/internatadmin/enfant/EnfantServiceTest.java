package com.daara.internatadmin.enfant;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.error.DuplicateEntityException;
import com.daara.internatadmin.common.support.TestTransactionManager;
import com.daara.internatadmin.common.tx.TransactionalRetry;
import com.daara.internatadmin.enfant.dto.CreateEnfantRequest;
import com.daara.internatadmin.enfant.dto.EnfantCountsResponse;
import com.daara.internatadmin.enfant.dto.EnfantResponse;
import com.daara.internatadmin.enfant.dto.UpdateEnfantRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnfantServiceTest {

    private final EnfantRepository repository = mock(EnfantRepository.class);
    private final TestTransactionManager transactionManager = new TestTransactionManager();
    private final EnfantService service = new EnfantService(repository, new TransactionalRetry(transactionManager));

    @Test
    void creation_matriculeSurLAnneeDEntreeEtTousLesChamps() {
        when(repository.countByMatriculeStartingWith("ENF-2025")).thenReturn(6L);
        List<Enfant> saved = captureSaves(0);

        EnfantResponse response = service.create(request(LocalDate.of(2018, 3, 2), null, LocalDate.of(2025, 9, 1)), false);

        assertThat(response.matricule()).isEqualTo("ENF-2025-0007");
        assertThat(response.fullName()).isEqualTo("Moussa Ndiaye");
        assertThat(response.status()).isEqualTo("ACTIF");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(2018, 3, 2));
        assertThat(response.approximateAge()).isNull();
        assertThat(response.guardianName()).isEqualTo("Ibrahima Ndiaye");
        assertThat(response.neighborhood()).isEqualTo("Médina");
        assertThat(response.roomGroup()).isEqualTo("Groupe A");
        assertThat(response.registrationFee()).isEqualByComparingTo("15000");
        assertThat(response.monthlyFee()).isEqualByComparingTo("10000");
        assertThat(saved.getFirst().getEntryDate()).isEqualTo(LocalDate.of(2025, 9, 1));
    }

    @Test
    void creation_ageApproximatifSeulAccepte() {
        captureSaves(0);
        EnfantResponse response = service.create(request(null, 8, LocalDate.of(2026, 1, 5)), false);
        assertThat(response.approximateAge()).isEqualTo(8);
        assertThat(response.birthDate()).isNull();
    }

    @Test
    void creation_niDateNiAge_refusAvecLeMessageDuMonolithe() {
        assertThatThrownBy(() -> service.create(request(null, null, LocalDate.of(2026, 1, 5)), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La date de naissance ou l'âge approximatif doit être renseigné");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void creation_doublonNonConfirme_409PossibleDuplicate() {
        when(repository.existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndGuardianPhone("Moussa", "Ndiaye", "771234567"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request(null, 8, LocalDate.of(2026, 1, 5)), false))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessage("Un enfant avec le même nom, prénom et téléphone de tuteur existe déjà. "
                        + "Si c'est intentionnel, renvoyez la requête avec confirmDuplicate=true.");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void creation_doublonConfirme_fratrieAcceptee() {
        captureSaves(0);
        EnfantResponse response = service.create(request(null, 8, LocalDate.of(2026, 1, 5)), true);
        assertThat(response.matricule()).isEqualTo("ENF-2026-0001");
        verify(repository, never()).existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndGuardianPhone(any(), any(), any());
    }

    @Test
    void creation_collisionDeMatricule_sequenceSuivanteDansUneNouvelleTransaction() {
        when(repository.countByMatriculeStartingWith("ENF-2026")).thenReturn(2L);
        List<Enfant> saved = captureSaves(2);

        EnfantResponse response = service.create(request(null, 8, LocalDate.of(2026, 1, 5)), false);

        assertThat(saved).extracting(Enfant::getMatricule).containsExactly("ENF-2026-0003", "ENF-2026-0004", "ENF-2026-0005");
        assertThat(response.matricule()).isEqualTo("ENF-2026-0005");
        assertThat(transactionManager.begins.get()).isEqualTo(3);
        assertThat(transactionManager.rollbacks.get()).isEqualTo(2);
    }

    @Test
    void creation_cinqCollisions_500EtJamais409() {
        captureSaves(5);
        assertThatThrownBy(() -> service.create(request(null, 8, LocalDate.of(2026, 1, 5)), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Impossible de générer un matricule unique après 5 tentatives");
    }

    @Test
    void modification_champsModifiablesSeulement() {
        Enfant enfant = existing();
        when(repository.findById(4L)).thenReturn(Optional.of(enfant));

        EnfantResponse response = service.update(4L, new UpdateEnfantRequest("Awa", "Fall", null, 9, "F", "Coumba Fall",
                "781112233", "Pikine", "Groupe B", new BigDecimal("20000"), new BigDecimal("12000")));

        assertThat(response.fullName()).isEqualTo("Awa Fall");
        assertThat(response.gender()).isEqualTo("F");
        assertThat(response.approximateAge()).isEqualTo(9);
        assertThat(response.monthlyFee()).isEqualByComparingTo("12000");
        assertThat(response.matricule()).isEqualTo("ENF-2026-0004");
        assertThat(response.entryDate()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(response.status()).isEqualTo("EN_VACANCES");
    }

    @Test
    void modification_niDateNiAge_refusee() {
        assertThatThrownBy(() -> service.update(4L, new UpdateEnfantRequest("Awa", "Fall", null, null, "F", "C",
                "781112233", null, null, BigDecimal.ONE, BigDecimal.ONE)))
                .hasMessage("La date de naissance ou l'âge approximatif doit être renseigné");
    }

    @Test
    void statut_change() {
        Enfant enfant = existing();
        when(repository.findById(4L)).thenReturn(Optional.of(enfant));
        assertThat(service.updateStatus(4L, StatutEnfant.SORTI).status()).isEqualTo("SORTI");
    }

    @Test
    void impressions_messagesDuMonolithe() {
        when(repository.findById(4L)).thenReturn(Optional.of(existing()));
        assertThat(service.recordCardPrint(4L))
                .isEqualTo(new PrintActionResponse(4L, "Impression de la carte enregistrée pour Moussa Ndiaye"));
        assertThat(service.recordCertificatePrint(4L))
                .isEqualTo(new PrintActionResponse(4L, "Impression du certificat enregistrée pour Moussa Ndiaye"));
    }

    @Test
    void introuvable_messagesDuMonolithe() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        when(repository.findByMatricule("ENF-1999-0001")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(99L)).hasMessage("Enfant introuvable : 99");
        assertThatThrownBy(() -> service.updateStatus(99L, StatutEnfant.ACTIF)).hasMessage("Enfant introuvable : 99");
        assertThatThrownBy(() -> service.recordCardPrint(99L)).hasMessage("Enfant introuvable : 99");
        assertThatThrownBy(() -> service.getByMatricule("ENF-1999-0001"))
                .hasMessage("Aucun enfant trouvé avec le matricule : ENF-1999-0001");
    }

    @Test
    void listesRechercheEtEffectifs() {
        when(repository.findAll()).thenReturn(List.of(existing()));
        when(repository.findByStatus(StatutEnfant.EN_VACANCES)).thenReturn(List.of(existing()));
        when(repository.findByStatusIn(Set.of(StatutEnfant.ACTIF, StatutEnfant.EN_VACANCES))).thenReturn(List.of(existing()));
        when(repository.findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase("ndia", "ndia")).thenReturn(List.of(existing()));
        when(repository.findByMatricule("ENF-2026-0004")).thenReturn(Optional.of(existing()));
        when(repository.countByStatus(StatutEnfant.ACTIF)).thenReturn(12L);
        when(repository.countByStatus(StatutEnfant.SORTI)).thenReturn(3L);
        when(repository.countByStatus(StatutEnfant.EN_VACANCES)).thenReturn(2L);
        when(repository.countByEntryDateBetween(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28))).thenReturn(4L);

        assertThat(service.listAll()).hasSize(1);
        assertThat(service.listByStatus(StatutEnfant.EN_VACANCES)).hasSize(1);
        assertThat(service.listByStatuses(Set.of(StatutEnfant.ACTIF, StatutEnfant.EN_VACANCES))).hasSize(1);
        assertThat(service.search("ndia")).hasSize(1);
        assertThat(service.getByMatricule("ENF-2026-0004").id()).isEqualTo(4L);
        assertThat(service.counts(YearMonth.of(2026, 2))).isEqualTo(new EnfantCountsResponse(12, 3, 2, 4));
    }

    private List<Enfant> captureSaves(int collisions) {
        List<Enfant> saved = new ArrayList<>();
        when(repository.saveAndFlush(any(Enfant.class))).thenAnswer(invocation -> {
            Enfant enfant = invocation.getArgument(0);
            saved.add(enfant);
            if (saved.size() <= collisions) {
                throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
            }
            enfant.setId((long) saved.size());
            return enfant;
        });
        return saved;
    }

    private static CreateEnfantRequest request(LocalDate birthDate, Integer approximateAge, LocalDate entryDate) {
        return new CreateEnfantRequest("Moussa", "Ndiaye", birthDate, approximateAge, "M", "Ibrahima Ndiaye",
                "771234567", "Médina", entryDate, "Groupe A", new BigDecimal("15000"), new BigDecimal("10000"));
    }

    private static Enfant existing() {
        Enfant enfant = new Enfant();
        enfant.setId(4L);
        enfant.setMatricule("ENF-2026-0004");
        enfant.setFirstName("Moussa");
        enfant.setLastName("Ndiaye");
        enfant.setApproximateAge(8);
        enfant.setGender("M");
        enfant.setGuardianName("Ibrahima Ndiaye");
        enfant.setGuardianPhone("771234567");
        enfant.setEntryDate(LocalDate.of(2026, 1, 5));
        enfant.setStatus(StatutEnfant.EN_VACANCES);
        enfant.setRegistrationFee(new BigDecimal("15000"));
        enfant.setMonthlyFee(new BigDecimal("10000"));
        return enfant;
    }
}
