package com.daara.internatadmin.paiement;

import com.daara.internatadmin.common.api.PrintActionResponse;
import com.daara.internatadmin.common.error.PaymentAlreadyExistsException;
import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.paiement.client.EmployeClient;
import com.daara.internatadmin.paiement.client.EmployeView;
import com.daara.internatadmin.paiement.client.EnfantClient;
import com.daara.internatadmin.paiement.client.EnfantView;
import com.daara.internatadmin.paiement.enfant.PaiementEnfant;
import com.daara.internatadmin.paiement.enfant.PaiementEnfantRecorder;
import com.daara.internatadmin.paiement.enfant.PaiementEnfantRepository;
import com.daara.internatadmin.paiement.enfant.PaiementEnfantService;
import com.daara.internatadmin.paiement.enfant.TypePaiementEnfant;
import com.daara.internatadmin.paiement.enfant.dto.InscriptionSuiviResponse;
import com.daara.internatadmin.paiement.enfant.dto.SuiviMensuelResponse;
import com.daara.internatadmin.paiement.salaire.PaiementSalaire;
import com.daara.internatadmin.paiement.salaire.PaiementSalaireRepository;
import com.daara.internatadmin.paiement.salaire.PaiementSalaireService;
import com.daara.internatadmin.paiement.salaire.dto.CreatePaiementSalaireRequest;
import com.daara.internatadmin.paiement.salaire.dto.PaiementSalaireResponse;
import com.daara.internatadmin.paiement.salaire.dto.SalaireSuiviResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Suivis, recus, annulations et salaires : calculs et messages identiques au monolithe. */
class PaiementServicesTest {

    private static final LocalDate OCTOBRE = LocalDate.of(2026, 10, 1);

    private final PaiementEnfantRepository enfantRepository = mock(PaiementEnfantRepository.class);
    private final EnfantClient enfantClient = mock(EnfantClient.class);
    private final PaiementEnfantService enfantService = new PaiementEnfantService(enfantRepository,
            mock(PaiementEnfantRecorder.class), enfantClient);

    private final PaiementSalaireRepository salaireRepository = mock(PaiementSalaireRepository.class);
    private final EmployeClient employeClient = mock(EmployeClient.class);
    private final PaiementSalaireService salaireService = new PaiementSalaireService(salaireRepository, employeClient);

    // ---------------------------------------------------------------- suivi des mensualites

    @Test
    void suiviMensuel_statutsMontantsEtTriParNom() {
        when(enfantClient.listByStatuses(List.of("ACTIF", "EN_VACANCES"))).thenReturn(List.of(
                enfant(1L, "Moussa Ndiaye"), enfant(2L, "Awa Fall"), enfant(3L, "Ibou Sow")));
        when(enfantRepository.findByPaymentTypeAndConcernedMonthAndStatus(TypePaiementEnfant.MENSUALITE, OCTOBRE, StatutPaiement.VALIDE))
                .thenReturn(List.of(mensualite(10L, 1L, "6000", "10000"), mensualite(11L, 1L, "4000", "10000"),
                        mensualite(12L, 2L, "6000", "10000")));

        List<SuiviMensuelResponse> suivi = enfantService.getSuiviMensuel(2026, 10, "ALL");

        assertThat(suivi).extracting(SuiviMensuelResponse::fullName).containsExactly("Awa Fall", "Ibou Sow", "Moussa Ndiaye");
        SuiviMensuelResponse awa = suivi.get(0);
        assertThat(awa.statutPaiement()).isEqualTo("PARTIEL");
        assertThat(awa.montantDu()).isEqualByComparingTo("10000");
        assertThat(awa.montantPaye()).isEqualByComparingTo("6000");
        assertThat(awa.montantRestant()).isEqualByComparingTo("4000");
        assertThat(awa.excedent()).isEqualByComparingTo("0");
        assertThat(awa.paiementIds()).containsExactly(12L);
        assertThat(suivi.get(1).statutPaiement()).isEqualTo("IMPAYE");
        assertThat(suivi.get(1).montantDu()).as("tarif actuel sans paiement").isEqualByComparingTo("10000");
        assertThat(suivi.get(1).paiementIds()).isEmpty();
        assertThat(suivi.get(2).statutPaiement()).isEqualTo("PAYE");
        assertThat(suivi.get(2).paiementIds()).containsExactly(10L, 11L);

        assertThat(enfantService.getSuiviMensuel(2026, 10, "IMPAYE")).extracting(SuiviMensuelResponse::fullName)
                .containsExactly("Ibou Sow");
        assertThat(enfantService.getSuiviMensuel(2026, 10, "INCONNU")).isEmpty();
    }

    // ---------------------------------------------------------------- suivi des inscriptions

    @Test
    void inscriptions_triParDateSiPayeSinonParNom() {
        when(enfantClient.listByStatuses(List.of("ACTIF", "EN_VACANCES"))).thenReturn(List.of(
                enfant(1L, "Moussa Ndiaye"), enfant(2L, "Awa Fall"), enfant(3L, "Ibou Sow")));
        when(enfantRepository.findByPaymentTypeAndStatus(TypePaiementEnfant.INSCRIPTION, StatutPaiement.VALIDE))
                .thenReturn(List.of(inscription(20L, 1L, LocalDate.of(2026, 9, 1)), inscription(21L, 3L, LocalDate.of(2026, 10, 3))));

        assertThat(enfantService.getInscriptions("PAYE")).extracting(InscriptionSuiviResponse::fullName)
                .containsExactly("Ibou Sow", "Moussa Ndiaye");
        List<InscriptionSuiviResponse> all = enfantService.getInscriptions("ALL");
        assertThat(all).extracting(InscriptionSuiviResponse::fullName).containsExactly("Awa Fall", "Ibou Sow", "Moussa Ndiaye");
        assertThat(all.get(0).statut()).isEqualTo("IMPAYE");
        assertThat(all.get(0).paymentDate()).isNull();
        assertThat(all.get(0).paiementId()).isNull();
        assertThat(all.get(0).montantAttendu()).isEqualByComparingTo("15000");
        assertThat(all.get(1).paiementId()).isEqualTo(21L);
        assertThat(enfantService.getInscriptions("IMPAYE")).extracting(InscriptionSuiviResponse::fullName).containsExactly("Awa Fall");
    }

    // ---------------------------------------------------------------- recus, impression, annulation

    @Test
    void recu_libellesDuMonolithe() {
        PaiementEnfant complet = mensualite(1L, 1L, "10000", "10000");
        complet.setIsPartial(false);
        PaiementEnfant partiel = mensualite(2L, 1L, "6000", "10000");
        partiel.setIsPartial(true);
        PaiementEnfant inscription = inscription(3L, 1L, LocalDate.of(2026, 9, 1));
        when(enfantRepository.findById(1L)).thenReturn(Optional.of(complet));
        when(enfantRepository.findById(2L)).thenReturn(Optional.of(partiel));
        when(enfantRepository.findById(3L)).thenReturn(Optional.of(inscription));

        assertThat(enfantService.getReceiptData(1L).libelleStatut()).isEqualTo("Paiement complet");
        assertThat(enfantService.getReceiptData(2L).libelleStatut()).isEqualTo("Paiement partiel");
        assertThat(enfantService.getReceiptData(2L).isPartial()).isTrue();
        assertThat(enfantService.getReceiptData(3L).libelleStatut()).isEqualTo("Frais d'inscription");
        assertThat(enfantService.getReceiptData(3L).isPartial()).isFalse();
        assertThat(enfantService.getReceiptData(3L).guardianName()).isEqualTo("Tuteur");
        assertThat(enfantService.recordReceiptPrint(1L))
                .isEqualTo(new PrintActionResponse(1L, "Impression du reçu enregistrée pour le paiement de Eleve 1"));
    }

    @Test
    void annulation_puisDoubleAnnulationEtIntrouvable() {
        PaiementEnfant p = mensualite(1L, 1L, "10000", "10000");
        when(enfantRepository.findById(1L)).thenReturn(Optional.of(p));
        when(enfantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(enfantService.cancel(1L).status()).isEqualTo(StatutPaiement.ANNULE);
        assertThatThrownBy(() -> enfantService.cancel(1L)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ce paiement est déjà annulé");
        assertThatThrownBy(() -> enfantService.cancel(99L)).hasMessage("Paiement introuvable : 99");
        assertThatThrownBy(() -> enfantService.getReceiptData(99L)).hasMessage("Paiement introuvable : 99");
    }

    // ---------------------------------------------------------------- salaires

    @Test
    void salaire_montantEgalAuSalaireDeBaseEtCopiesFigees() {
        when(employeClient.get(4L)).thenReturn(employe(4L, "Serigne Mbaye", "75000.00"));
        when(salaireRepository.saveAndFlush(any(PaiementSalaire.class))).thenAnswer(i -> i.getArgument(0));

        PaiementSalaireResponse response = salaireService.create(
                new CreatePaiementSalaireRequest(4L, ModePaiement.VIREMENT, 2026, 10, null), new CurrentUser(1L, "admin", "ADMIN"));

        assertThat(response.amount()).isEqualByComparingTo("75000");
        assertThat(response.employeFullName()).isEqualTo("Serigne Mbaye");
        assertThat(response.employeMatricule()).isEqualTo("EMP-2026-0004");
        assertThat(response.concernedMonth()).isEqualTo(OCTOBRE);
        assertThat(response.registeredByUsername()).isEqualTo("admin");
        assertThat(response.status()).isEqualTo(StatutPaiement.VALIDE);
    }

    @Test
    void salaireDejaPaye_409AvecLeMessageDuMonolithe() {
        when(employeClient.get(4L)).thenReturn(employe(4L, "Serigne Mbaye", "75000.00"));
        when(salaireRepository.saveAndFlush(any(PaiementSalaire.class))).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> salaireService.create(new CreatePaiementSalaireRequest(4L, ModePaiement.ESPECES, 2026, 10, null),
                new CurrentUser(1L, "admin", "ADMIN")))
                .isInstanceOf(PaymentAlreadyExistsException.class)
                .hasMessage("Le salaire de 10/2026 a déjà été enregistré pour cet employé");
    }

    @Test
    void suiviDesSalaires_payeImpayeTriParNom() {
        when(employeClient.listByStatuses(List.of("ACTIF", "EN_CONGE"))).thenReturn(List.of(
                employe(1L, "Serigne Mbaye", "75000"), employe(2L, "Fatou Gueye", "40000")));
        PaiementSalaire paye = new PaiementSalaire();
        paye.setId(30L);
        paye.setEmployeId(1L);
        paye.setPaymentDate(LocalDate.of(2026, 10, 30));
        when(salaireRepository.findByConcernedMonthAndStatus(OCTOBRE, StatutPaiement.VALIDE)).thenReturn(List.of(paye));

        List<SalaireSuiviResponse> suivi = salaireService.getSuiviMensuel(2026, 10, "ALL");

        assertThat(suivi).extracting(SalaireSuiviResponse::fullName).containsExactly("Fatou Gueye", "Serigne Mbaye");
        assertThat(suivi.get(0).statut()).isEqualTo("IMPAYE");
        assertThat(suivi.get(0).montantAttendu()).isEqualByComparingTo("40000");
        assertThat(suivi.get(1).statut()).isEqualTo("PAYE");
        assertThat(suivi.get(1).paiementId()).isEqualTo(30L);
        assertThat(salaireService.getSuiviMensuel(2026, 10, "PAYE")).hasSize(1);
    }

    private static EnfantView enfant(Long id, String fullName) {
        return new EnfantView(id, "ENF-2026-000" + id, fullName, "Tuteur " + id, "77000000" + id, "ACTIF",
                new BigDecimal("15000.00"), new BigDecimal("10000.00"));
    }

    private static EmployeView employe(Long id, String fullName, String salary) {
        return new EmployeView(id, "EMP-2026-000" + id, fullName, "MAITRE_CORANIQUE", "ACTIF", new BigDecimal(salary));
    }

    private static PaiementEnfant mensualite(Long id, Long enfantId, String amount, String expected) {
        PaiementEnfant p = new PaiementEnfant();
        p.setId(id);
        p.setEnfantId(enfantId);
        p.setEnfantFullName("Eleve " + enfantId);
        p.setEnfantMatricule("ENF-2026-000" + enfantId);
        p.setGuardianName("Tuteur");
        p.setPaymentType(TypePaiementEnfant.MENSUALITE);
        p.setPaymentMethod(ModePaiement.ESPECES);
        p.setAmount(new BigDecimal(amount));
        p.setExpectedAmount(new BigDecimal(expected));
        p.setConcernedMonth(OCTOBRE);
        return p;
    }

    private static PaiementEnfant inscription(Long id, Long enfantId, LocalDate paymentDate) {
        PaiementEnfant p = mensualite(id, enfantId, "15000", "15000");
        p.setPaymentType(TypePaiementEnfant.INSCRIPTION);
        p.setExpectedAmount(null);
        p.setConcernedMonth(null);
        p.setPaymentDate(paymentDate);
        return p;
    }
}
