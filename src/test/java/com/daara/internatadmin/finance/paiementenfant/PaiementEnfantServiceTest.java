package com.daara.internatadmin.finance.paiementenfant;

import com.daara.internatadmin.common.AmountMismatchException;
import com.daara.internatadmin.common.PartialPaymentException;
import com.daara.internatadmin.enfant.Enfant;
import com.daara.internatadmin.enfant.EnfantRepository;
import com.daara.internatadmin.finance.paiementenfant.dto.CreatePaiementEnfantRequest;
import com.daara.internatadmin.finance.paiementenfant.dto.PaiementEnfantResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaiementEnfantServiceTest {

    private static final long ENFANT_ID = 1L;
    private static final LocalDate MARS = LocalDate.of(2026, 3, 1);

    private final PaiementEnfantRepository paiements = mock(PaiementEnfantRepository.class);
    private final EnfantRepository enfants = mock(EnfantRepository.class);
    private final PaiementLocks locks = mock(PaiementLocks.class);
    private final PaiementEnfantService service = new PaiementEnfantService(paiements, enfants, locks);

    private final Enfant enfant = new Enfant();

    @BeforeEach
    void setUp() {
        enfant.setId(ENFANT_ID);
        enfant.setFirstName("Awa");
        enfant.setLastName("Diop");
        enfant.setMonthlyFee(new BigDecimal("10000"));
        enfant.setRegistrationFee(new BigDecimal("5000"));
        when(enfants.findById(ENFANT_ID)).thenReturn(Optional.of(enfant));
        when(paiements.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static CreatePaiementEnfantRequest mensualite(String amount, boolean confirmPartial) {
        return new CreatePaiementEnfantRequest(ENFANT_ID, TypePaiementEnfant.MENSUALITE, ModePaiement.ESPECES,
                new BigDecimal(amount), 2026, 3, null, confirmPartial);
    }

    private PaiementEnfantResponse payer(CreatePaiementEnfantRequest dto) {
        return service.create(dto, 9L, "gestionnaire");
    }

    private void dejaPaye(String amount) {
        PaiementEnfant existant = new PaiementEnfant();
        existant.setEnfant(enfant);
        existant.setAmount(new BigDecimal(amount));
        existant.setExpectedAmount(new BigDecimal("10000"));
        when(paiements.findByPaymentTypeAndConcernedMonthAndStatus(
                TypePaiementEnfant.MENSUALITE, MARS, StatutPaiement.VALIDE)).thenReturn(List.of(existant));
    }

    @Test
    void mensualiteComplete_soldeNul() {
        PaiementEnfantResponse response = payer(mensualite("10000", false));

        assertThat(response.balanceAfter()).isEqualByComparingTo("0");
        assertThat(response.isPartial()).isFalse();
    }

    @Test
    void partielNonConfirme_refuse() {
        assertThatThrownBy(() -> payer(mensualite("4000", false))).isInstanceOf(PartialPaymentException.class);
        verify(paiements, never()).saveAndFlush(any());
    }

    @Test
    void partielConfirme_soldeRestant() {
        PaiementEnfantResponse response = payer(mensualite("4000", true));

        assertThat(response.balanceAfter()).isEqualByComparingTo("6000");
        assertThat(response.isPartial()).isTrue();
    }

    @Test
    void depassementDuSoldeRestant_refuseMemeConfirme() {
        dejaPaye("7000");

        assertThatThrownBy(() -> payer(mensualite("4000", true)))
                .isInstanceOf(AmountMismatchException.class)
                .hasMessageContaining("3000");
        verify(paiements, never()).saveAndFlush(any());
    }

    @Test
    void mensualiteSoldee_refusee() {
        dejaPaye("10000");

        assertThatThrownBy(() -> payer(mensualite("1", true)))
                .isInstanceOf(AmountMismatchException.class)
                .hasMessageContaining("déjà entièrement payée");
    }

    @Test
    void inscription_montantExactObligatoire() {
        CreatePaiementEnfantRequest dto = new CreatePaiementEnfantRequest(ENFANT_ID, TypePaiementEnfant.INSCRIPTION,
                ModePaiement.WAVE, new BigDecimal("4999"), null, null, null, true);

        assertThatThrownBy(() -> payer(dto)).isInstanceOf(AmountMismatchException.class);
    }

    @Test
    void mensualiteSansMois_refusee() {
        CreatePaiementEnfantRequest dto = new CreatePaiementEnfantRequest(ENFANT_ID, TypePaiementEnfant.MENSUALITE,
                ModePaiement.WAVE, new BigDecimal("10000"), null, null, null, false);

        assertThatThrownBy(() -> payer(dto)).isInstanceOf(IllegalArgumentException.class);
    }

    /** E2 : le deja paye ne se lit qu'une fois le verrou obtenu. */
    @Test
    void verrouPrisAvantLaLectureDuDejaPaye() {
        payer(mensualite("10000", false));

        InOrder order = inOrder(locks, paiements);
        order.verify(locks).lockMensualite(ENFANT_ID, MARS);
        order.verify(paiements).findByPaymentTypeAndConcernedMonthAndStatus(
                TypePaiementEnfant.MENSUALITE, MARS, StatutPaiement.VALIDE);
    }
}
