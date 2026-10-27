package com.daara.internatadmin.paiement;

import com.daara.internatadmin.common.error.AmountMismatchException;
import com.daara.internatadmin.common.error.PartialPaymentException;
import com.daara.internatadmin.common.error.PaymentAlreadyExistsException;
import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.paiement.client.EnfantView;
import com.daara.internatadmin.paiement.enfant.PaiementEnfant;
import com.daara.internatadmin.paiement.enfant.PaiementEnfantRecorder;
import com.daara.internatadmin.paiement.enfant.PaiementEnfantRepository;
import com.daara.internatadmin.paiement.enfant.TypePaiementEnfant;
import com.daara.internatadmin.paiement.enfant.dto.CreatePaiementEnfantRequest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Regles d'enregistrement d'un paiement d'eleve : montants, messages et codes du monolithe. */
class PaiementEnfantRecorderTest {

    private static final LocalDate OCTOBRE = LocalDate.of(2026, 10, 1);
    private static final EnfantView ENFANT = new EnfantView(7L, "ENF-2026-0007", "Moussa Ndiaye", "Ibrahima Ndiaye",
            "771234567", "ACTIF", new BigDecimal("15000.00"), new BigDecimal("10000.00"));
    private static final CurrentUser USER = new CurrentUser(5L, "awa.diop", "GESTIONNAIRE");

    private final PaiementEnfantRepository repository = mock(PaiementEnfantRepository.class);
    private final PaiementLocks locks = mock(PaiementLocks.class);
    private final PaiementEnfantRecorder recorder = new PaiementEnfantRecorder(repository, locks);

    PaiementEnfantRecorderTest() {
        when(repository.saveAndFlush(any(PaiementEnfant.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void mensualiteComplete_soldeNulEtCopiesFigees() {
        PaiementEnfant p = recorder.record(mensualite("10000", false), ENFANT, USER);

        assertThat(p.getConcernedMonth()).isEqualTo(OCTOBRE);
        assertThat(p.getExpectedAmount()).isEqualByComparingTo("10000");
        assertThat(p.getBalanceAfter()).isEqualByComparingTo("0");
        assertThat(p.getIsPartial()).isFalse();
        assertThat(p.getExcessAmount()).isEqualByComparingTo("0");
        assertThat(p.getEnfantMatricule()).isEqualTo("ENF-2026-0007");
        assertThat(p.getEnfantFullName()).isEqualTo("Moussa Ndiaye");
        assertThat(p.getGuardianName()).isEqualTo("Ibrahima Ndiaye");
        assertThat(p.getRegisteredByUserId()).isEqualTo(5L);
        assertThat(p.getRegisteredByUsername()).isEqualTo("awa.diop");
        assertThat(p.getStatus()).isEqualTo(StatutPaiement.VALIDE);
    }

    @Test
    void mensualite_verrouPrisAvantLaLectureDesPaiementsExistants() {
        recorder.record(mensualite("10000", false), ENFANT, USER);

        var order = inOrder(locks, repository);
        order.verify(locks).lockMensualite(7L, OCTOBRE);
        order.verify(repository).findByEnfantIdAndPaymentTypeAndConcernedMonthAndStatus(
                7L, TypePaiementEnfant.MENSUALITE, OCTOBRE, StatutPaiement.VALIDE);
    }

    @Test
    void mensualitePartielleNonConfirmee_409PartialPaymentNotConfirmed() {
        assertThatThrownBy(() -> recorder.record(mensualite("6000", false), ENFANT, USER))
                .isInstanceOf(PartialPaymentException.class)
                .hasMessage("Le montant saisi (6000) est inférieur au solde restant dû (10000.00). "
                        + "Si c'est un paiement partiel intentionnel, renvoyez avec confirmPartial=true.");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void mensualitePartielleConfirmee_soldeRestant() {
        PaiementEnfant p = recorder.record(mensualite("6000", true), ENFANT, USER);
        assertThat(p.getBalanceAfter()).isEqualByComparingTo("4000");
        assertThat(p.getIsPartial()).isTrue();
    }

    @Test
    void montantSuperieurAuRestant_409AmountMismatch() {
        existing(paiement("6000", "10000.00"));

        assertThatThrownBy(() -> recorder.record(mensualite("5000", true), ENFANT, USER))
                .isInstanceOf(AmountMismatchException.class)
                .hasMessage("Le montant saisi (5000) dépasse le solde restant dû (4000.00). Aucun dépassement n'est autorisé.");
    }

    @Test
    void montantEgalAuRestant_soldeLaMensualiteSansConfirmation() {
        existing(paiement("6000", "10000.00"));

        PaiementEnfant p = recorder.record(mensualite("4000", false), ENFANT, USER);
        assertThat(p.getBalanceAfter()).isEqualByComparingTo("0");
        assertThat(p.getIsPartial()).isFalse();
    }

    @Test
    void mensualiteDejaSoldee_409AmountMismatch() {
        existing(paiement("6000", "10000.00"), paiement("4000", "10000.00"));

        assertThatThrownBy(() -> recorder.record(mensualite("1000", true), ENFANT, USER))
                .isInstanceOf(AmountMismatchException.class)
                .hasMessage("La mensualité de 10/2026 est déjà entièrement payée pour cet enfant");
    }

    @Test
    void montantDuFigeAuPremierPaiement_unNouveauTarifNeReecritPasLHistoire() {
        existing(paiement("6000", "10000.00"));
        EnfantView nouveauTarif = new EnfantView(7L, "ENF-2026-0007", "Moussa Ndiaye", "Ibrahima Ndiaye",
                "771234567", "ACTIF", new BigDecimal("15000.00"), new BigDecimal("12000.00"));

        PaiementEnfant p = recorder.record(mensualite("4000", false), nouveauTarif, USER);

        assertThat(p.getExpectedAmount()).isEqualByComparingTo("10000");
        assertThat(p.getBalanceAfter()).isEqualByComparingTo("0");
    }

    @Test
    void mensualiteSansAnneeOuMois_400() {
        CreatePaiementEnfantRequest sansMois = new CreatePaiementEnfantRequest(7L, TypePaiementEnfant.MENSUALITE,
                ModePaiement.WAVE, new BigDecimal("10000"), 2026, null, null, false);

        assertThatThrownBy(() -> recorder.record(sansMois, ENFANT, USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("L'année et le mois concernés sont requis pour une mensualité");
        verifyNoInteractions(locks);
    }

    @Test
    void inscriptionExacte_sansMoisNiSolde() {
        PaiementEnfant p = recorder.record(inscription("15000"), ENFANT, USER);

        assertThat(p.getPaymentType()).isEqualTo(TypePaiementEnfant.INSCRIPTION);
        assertThat(p.getConcernedMonth()).isNull();
        assertThat(p.getBalanceAfter()).isNull();
        assertThat(p.getIsPartial()).isNull();
        assertThat(p.getExpectedAmount()).isNull();
        verifyNoInteractions(locks);
    }

    @Test
    void inscriptionDUnAutreMontant_409AmountMismatch() {
        assertThatThrownBy(() -> recorder.record(inscription("10000"), ENFANT, USER))
                .isInstanceOf(AmountMismatchException.class)
                .hasMessage("Le montant des frais d'inscription doit être exactement 15000.00. Aucune dérogation n'est autorisée.");
    }

    @Test
    void inscriptionDejaPayee_409PaymentAlreadyExists() {
        when(repository.saveAndFlush(any(PaiementEnfant.class))).thenThrow(new DataIntegrityViolationException("uq_paiement_inscription"));

        assertThatThrownBy(() -> recorder.record(inscription("15000"), ENFANT, USER))
                .isInstanceOf(PaymentAlreadyExistsException.class)
                .hasMessage("Les frais d'inscription ont déjà été enregistrés pour cet enfant");
    }

    @Test
    void dateDePaiementFournie_conservee() {
        CreatePaiementEnfantRequest dto = new CreatePaiementEnfantRequest(7L, TypePaiementEnfant.INSCRIPTION,
                ModePaiement.ORANGE_MONEY, new BigDecimal("15000"), null, null, LocalDate.of(2026, 9, 28), false);
        assertThat(recorder.record(dto, ENFANT, USER).getPaymentDate()).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    private void existing(PaiementEnfant... paiements) {
        when(repository.findByEnfantIdAndPaymentTypeAndConcernedMonthAndStatus(
                7L, TypePaiementEnfant.MENSUALITE, OCTOBRE, StatutPaiement.VALIDE)).thenReturn(List.of(paiements));
    }

    private static PaiementEnfant paiement(String amount, String expected) {
        PaiementEnfant p = new PaiementEnfant();
        p.setAmount(new BigDecimal(amount));
        p.setExpectedAmount(new BigDecimal(expected));
        return p;
    }

    private static CreatePaiementEnfantRequest mensualite(String amount, boolean confirmPartial) {
        return new CreatePaiementEnfantRequest(7L, TypePaiementEnfant.MENSUALITE, ModePaiement.ESPECES,
                new BigDecimal(amount), 2026, 10, null, confirmPartial);
    }

    private static CreatePaiementEnfantRequest inscription(String amount) {
        return new CreatePaiementEnfantRequest(7L, TypePaiementEnfant.INSCRIPTION, ModePaiement.ESPECES,
                new BigDecimal(amount), null, null, null, false);
    }
}
