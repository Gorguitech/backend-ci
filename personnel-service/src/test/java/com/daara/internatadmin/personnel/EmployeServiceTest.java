package com.daara.internatadmin.personnel;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.error.DuplicateEntityException;
import com.daara.internatadmin.common.support.TestTransactionManager;
import com.daara.internatadmin.common.tx.MatriculeAllocator;
import com.daara.internatadmin.common.tx.TransactionalRetry;
import com.daara.internatadmin.personnel.dto.CreateEmployeRequest;
import com.daara.internatadmin.personnel.dto.EmployeCountsResponse;
import com.daara.internatadmin.personnel.dto.EmployeResponse;
import com.daara.internatadmin.personnel.dto.UpdateEmployeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
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

class EmployeServiceTest {

    private final EmployeRepository repository = mock(EmployeRepository.class);
    private final TestTransactionManager transactionManager = new TestTransactionManager();
    private final EmployeService service = new EmployeService(repository,
            new MatriculeAllocator(new TransactionalRetry(transactionManager)));

    @Test
    void creation_matriculeSurLAnneeDEmbaucheEtTousLesChamps() {
        when(repository.countByMatriculeStartingWith("EMP-2024")).thenReturn(2L);
        captureSaves(0);

        EmployeResponse response = service.create(request(LocalDate.of(2024, 9, 1)), false);

        assertThat(response.matricule()).isEqualTo("EMP-2024-0003");
        assertThat(response.fullName()).isEqualTo("Serigne Mbaye");
        assertThat(response.phone()).isEqualTo("776665544");
        assertThat(response.function()).isEqualTo("MAITRE_CORANIQUE");
        assertThat(response.contractType()).isEqualTo("TEMPS_PLEIN");
        assertThat(response.baseSalary()).isEqualByComparingTo("75000");
        assertThat(response.hireDate()).isEqualTo(LocalDate.of(2024, 9, 1));
        assertThat(response.status()).isEqualTo("ACTIF");
    }

    @Test
    void creation_doublonNonConfirme_messageExactDuMonolithe() {
        when(repository.existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndPhone("Serigne", "Mbaye", "776665544"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request(LocalDate.of(2026, 1, 5)), false))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessage("Un employé avec le même nom, prénom et téléphone existe déjà. ");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void creation_doublonConfirme_controleNonFait() {
        captureSaves(0);
        assertThat(service.create(request(LocalDate.of(2026, 1, 5)), true).matricule()).isEqualTo("EMP-2026-0001");
        verify(repository, never()).existsByFirstNameIgnoreCaseAndLastNameIgnoreCaseAndPhone(any(), any(), any());
    }

    @Test
    void creation_collisionPuisSucces_etCinqCollisions() {
        List<Employe> saved = captureSaves(1);
        assertThat(service.create(request(LocalDate.of(2026, 1, 5)), false).matricule()).isEqualTo("EMP-2026-0002");
        assertThat(saved).extracting(Employe::getMatricule).containsExactly("EMP-2026-0001", "EMP-2026-0002");

        captureSaves(5);
        assertThatThrownBy(() -> service.create(request(LocalDate.of(2026, 1, 5)), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Impossible de générer un matricule unique après 5 tentatives");
    }

    @Test
    void modification_champsModifiablesSeulement() {
        when(repository.findById(3L)).thenReturn(Optional.of(existing()));

        EmployeResponse response = service.update(3L, new UpdateEmployeRequest("Fatou", "Gueye", "781112233",
                FonctionEmploye.CUISINIER, TypeContrat.PARTIEL, new BigDecimal("50000")));

        assertThat(response.fullName()).isEqualTo("Fatou Gueye");
        assertThat(response.function()).isEqualTo("CUISINIER");
        assertThat(response.contractType()).isEqualTo("PARTIEL");
        assertThat(response.baseSalary()).isEqualByComparingTo("50000");
        assertThat(response.matricule()).isEqualTo("EMP-2026-0003");
        assertThat(response.hireDate()).isEqualTo(LocalDate.of(2026, 1, 5));
        assertThat(response.status()).isEqualTo("EN_CONGE");
    }

    @Test
    void statutEtImpressions() {
        when(repository.findById(3L)).thenReturn(Optional.of(existing()));
        assertThat(service.updateStatus(3L, StatutEmploye.ANCIEN).status()).isEqualTo("ANCIEN");
        assertThat(service.recordCardPrint(3L))
                .isEqualTo(new PrintActionResponse(3L, "Impression de la carte enregistrée pour Serigne Mbaye"));
        assertThat(service.recordCertificatePrint(3L))
                .isEqualTo(new PrintActionResponse(3L, "Impression du certificat enregistrée pour Serigne Mbaye"));
    }

    @Test
    void introuvable_messagesDuMonolithe() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        when(repository.findByMatricule("EMP-1999-0001")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(99L)).hasMessage("Employé introuvable : 99");
        assertThatThrownBy(() -> service.updateStatus(99L, StatutEmploye.ACTIF)).hasMessage("Employé introuvable : 99");
        assertThatThrownBy(() -> service.getByMatricule("EMP-1999-0001"))
                .hasMessage("Aucun employé trouvé avec le matricule : EMP-1999-0001");
    }

    @Test
    void listesRechercheEtEffectifs() {
        when(repository.findAll()).thenReturn(List.of(existing()));
        when(repository.findByStatus(StatutEmploye.EN_CONGE)).thenReturn(List.of(existing()));
        when(repository.findByStatusIn(Set.of(StatutEmploye.ACTIF, StatutEmploye.EN_CONGE))).thenReturn(List.of(existing()));
        when(repository.findByLastNameContainingIgnoreCaseOrFirstNameContainingIgnoreCase("mba", "mba")).thenReturn(List.of(existing()));
        when(repository.findByMatricule("EMP-2026-0003")).thenReturn(Optional.of(existing()));
        when(repository.countByStatus(StatutEmploye.ACTIF)).thenReturn(6L);
        when(repository.countByStatus(StatutEmploye.EN_CONGE)).thenReturn(1L);
        when(repository.countByStatus(StatutEmploye.ANCIEN)).thenReturn(2L);

        assertThat(service.listAll()).hasSize(1);
        assertThat(service.listByStatus(StatutEmploye.EN_CONGE)).hasSize(1);
        assertThat(service.listByStatuses(Set.of(StatutEmploye.ACTIF, StatutEmploye.EN_CONGE))).hasSize(1);
        assertThat(service.search("mba")).hasSize(1);
        assertThat(service.getByMatricule("EMP-2026-0003").id()).isEqualTo(3L);
        assertThat(service.counts()).isEqualTo(new EmployeCountsResponse(6, 1, 2));
    }

    private List<Employe> captureSaves(int collisions) {
        List<Employe> saved = new ArrayList<>();
        when(repository.saveAndFlush(any(Employe.class))).thenAnswer(invocation -> {
            Employe employe = invocation.getArgument(0);
            saved.add(employe);
            if (saved.size() <= collisions) {
                throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
            }
            employe.setId((long) saved.size());
            return employe;
        });
        return saved;
    }

    private static CreateEmployeRequest request(LocalDate hireDate) {
        return new CreateEmployeRequest("Serigne", "Mbaye", "776665544", FonctionEmploye.MAITRE_CORANIQUE,
                TypeContrat.TEMPS_PLEIN, new BigDecimal("75000"), hireDate);
    }

    private static Employe existing() {
        Employe employe = new Employe();
        employe.setId(3L);
        employe.setMatricule("EMP-2026-0003");
        employe.setFirstName("Serigne");
        employe.setLastName("Mbaye");
        employe.setPhone("776665544");
        employe.setFunction(FonctionEmploye.MAITRE_CORANIQUE);
        employe.setContractType(TypeContrat.TEMPS_PLEIN);
        employe.setBaseSalary(new BigDecimal("75000"));
        employe.setHireDate(LocalDate.of(2026, 1, 5));
        employe.setStatus(StatutEmploye.EN_CONGE);
        return employe;
    }
}
