package com.daara.internatadmin.comptabilite;

import com.daara.internatadmin.common.error.DuplicateEntityException;
import com.daara.internatadmin.common.security.CurrentUser;
import com.daara.internatadmin.comptabilite.categorie.CategorieDepense;
import com.daara.internatadmin.comptabilite.categorie.CategorieDepenseRepository;
import com.daara.internatadmin.comptabilite.categorie.CategorieDepenseService;
import com.daara.internatadmin.comptabilite.categorie.dto.CreateCategorieDepenseRequest;
import com.daara.internatadmin.comptabilite.client.PaiementClient;
import com.daara.internatadmin.comptabilite.client.TotauxPaiementsView;
import com.daara.internatadmin.comptabilite.depense.Depense;
import com.daara.internatadmin.comptabilite.depense.DepenseRepository;
import com.daara.internatadmin.comptabilite.depense.DepenseService;
import com.daara.internatadmin.comptabilite.depense.dto.CreateDepenseRequest;
import com.daara.internatadmin.comptabilite.depense.dto.DepenseResponse;
import com.daara.internatadmin.comptabilite.recette.AutreRecette;
import com.daara.internatadmin.comptabilite.recette.AutreRecetteRepository;
import com.daara.internatadmin.comptabilite.recette.AutreRecetteService;
import com.daara.internatadmin.comptabilite.recette.dto.CreateAutreRecetteRequest;
import com.daara.internatadmin.comptabilite.tresorerie.TresorerieService;
import com.daara.internatadmin.comptabilite.tresorerie.dto.TresorerieResponse;
import com.daara.internatadmin.comptabilite.tresorerie.dto.TresorerieResponse.LigneDetail;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regles et messages du monolithe pour les depenses, les recettes, les categories et la tresorerie. */
class ComptabiliteServicesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 10, 0);
    private static final CurrentUser USER = new CurrentUser(5L, "awa.diop", "GESTIONNAIRE");

    private final CategorieDepenseRepository categorieRepository = mock(CategorieDepenseRepository.class);
    private final DepenseRepository depenseRepository = mock(DepenseRepository.class);
    private final AutreRecetteRepository recetteRepository = mock(AutreRecetteRepository.class);
    private final PaiementClient paiementClient = mock(PaiementClient.class);

    private final DepenseService depenseService = new DepenseService(depenseRepository, categorieRepository, CLOCK);
    private final AutreRecetteService recetteService = new AutreRecetteService(recetteRepository, CLOCK);
    private final CategorieDepenseService categorieService = new CategorieDepenseService(categorieRepository);
    private final TresorerieService tresorerieService = new TresorerieService(paiementClient, recetteRepository, depenseRepository);

    // ---------------------------------------------------------------- depenses

    @Test
    void depense_dateDuJourParDefautEtDoublonChercheSurLesDeuxDernieresMinutes() {
        when(categorieRepository.findById(1L)).thenReturn(Optional.of(categorie(1L, "Riz", true)));
        when(depenseRepository.save(any(Depense.class))).thenAnswer(i -> i.getArgument(0));

        DepenseResponse response = depenseService.create(new CreateDepenseRequest(1L, new BigDecimal("25000"), null, "Sac de riz", false), USER);

        assertThat(response.expenseDate()).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(response.categorieName()).isEqualTo("Riz");
        assertThat(response.registeredByUsername()).isEqualTo("awa.diop");
        assertThat(response.status()).isEqualTo(StatutMouvement.VALIDE);
        verify(depenseRepository).existsByCategorieIdAndAmountAndExpenseDateAndRegisteredByUserIdAndCreatedAtAfter(
                1L, new BigDecimal("25000"), LocalDate.of(2026, 10, 27), 5L, NOW.minusMinutes(2));
    }

    @Test
    void depense_doublonNonConfirme_409AvecLeMessageDuMonolithe() {
        when(categorieRepository.findById(1L)).thenReturn(Optional.of(categorie(1L, "Riz", true)));
        when(depenseRepository.existsByCategorieIdAndAmountAndExpenseDateAndRegisteredByUserIdAndCreatedAtAfter(
                eq(1L), any(), any(), eq(5L), any())).thenReturn(true);

        assertThatThrownBy(() -> depenseService.create(new CreateDepenseRequest(1L, BigDecimal.TEN, null, null, false), USER))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessage("Une dépense identique (même catégorie, montant et date) vient d'être enregistrée il y a moins "
                        + "de 2 minutes. Si c'est intentionnel, renvoyez la requête avec confirmDuplicate=true.");
        verify(depenseRepository, never()).save(any());
    }

    @Test
    void depense_doublonConfirme_controleNonFait() {
        when(categorieRepository.findById(1L)).thenReturn(Optional.of(categorie(1L, "Riz", true)));
        when(depenseRepository.save(any(Depense.class))).thenAnswer(i -> i.getArgument(0));

        depenseService.create(new CreateDepenseRequest(1L, BigDecimal.TEN, null, null, true), USER);

        verify(depenseRepository, never()).existsByCategorieIdAndAmountAndExpenseDateAndRegisteredByUserIdAndCreatedAtAfter(
                any(), any(), any(), any(), any());
    }

    @Test
    void depense_categorieInconnueOuDesactivee_400() {
        when(categorieRepository.findById(9L)).thenReturn(Optional.empty());
        when(categorieRepository.findById(2L)).thenReturn(Optional.of(categorie(2L, "Gaz", false)));

        assertThatThrownBy(() -> depenseService.create(new CreateDepenseRequest(9L, BigDecimal.TEN, null, null, false), USER))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Catégorie introuvable : 9");
        assertThatThrownBy(() -> depenseService.create(new CreateDepenseRequest(2L, BigDecimal.TEN, null, null, false), USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La catégorie 'Gaz' est désactivée et ne peut plus être utilisée");
    }

    @Test
    void depense_annulationImpressionEtIntrouvable() {
        Depense depense = new Depense();
        depense.setId(4L);
        depense.setCategorie(categorie(1L, "Riz", true));
        depense.setAmount(BigDecimal.TEN);
        when(depenseRepository.findById(4L)).thenReturn(Optional.of(depense));
        when(depenseRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(depenseService.recordReceiptPrint(4L).message()).isEqualTo("Impression du reçu enregistrée pour la dépense #4");
        assertThat(depenseService.cancel(4L).status()).isEqualTo(StatutMouvement.ANNULE);
        assertThatThrownBy(() -> depenseService.cancel(4L)).hasMessage("Cette dépense est déjà annulée");
        assertThatThrownBy(() -> depenseService.getReceiptData(99L)).hasMessage("Dépense introuvable : 99");
    }

    // ---------------------------------------------------------------- recettes

    @Test
    void recette_doublonEtDateParDefaut() {
        when(recetteRepository.save(any(AutreRecette.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(recetteService.create(new CreateAutreRecetteRequest("Don", BigDecimal.TEN, null, null, false), USER).recetteDate())
                .isEqualTo(LocalDate.of(2026, 10, 27));
        verify(recetteRepository).existsBySourceAndAmountAndRecetteDateAndRegisteredByUserIdAndCreatedAtAfter(
                "Don", BigDecimal.TEN, LocalDate.of(2026, 10, 27), 5L, NOW.minusMinutes(2));

        when(recetteRepository.existsBySourceAndAmountAndRecetteDateAndRegisteredByUserIdAndCreatedAtAfter(
                eq("Don"), any(), any(), eq(5L), any())).thenReturn(true);
        assertThatThrownBy(() -> recetteService.create(new CreateAutreRecetteRequest("Don", BigDecimal.TEN, null, null, false), USER))
                .isInstanceOf(DuplicateEntityException.class)
                .hasMessage("Une recette identique (même source, montant et date) vient d'être enregistrée il y a moins "
                        + "de 2 minutes. Si c'est intentionnel, renvoyez la requête avec confirmDuplicate=true.");
    }

    @Test
    void recette_annulationImpressionEtIntrouvable() {
        AutreRecette recette = new AutreRecette();
        recette.setId(3L);
        when(recetteRepository.findById(3L)).thenReturn(Optional.of(recette));
        when(recetteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(recetteService.recordReceiptPrint(3L).message()).isEqualTo("Impression du reçu enregistrée pour la recette #3");
        assertThat(recetteService.cancel(3L).status()).isEqualTo(StatutMouvement.ANNULE);
        assertThatThrownBy(() -> recetteService.cancel(3L)).hasMessage("Cette recette est déjà annulée");
        assertThatThrownBy(() -> recetteService.cancel(99L)).hasMessage("Recette introuvable : 99");
    }

    // ---------------------------------------------------------------- categories

    @Test
    void categorie_nomDejaPris_400AuLieuDu500DuMonolithe() {
        when(categorieRepository.saveAndFlush(any(CategorieDepense.class))).thenThrow(new DataIntegrityViolationException("uq"));
        assertThatThrownBy(() -> categorieService.create(new CreateCategorieDepenseRequest("Riz", null)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La catégorie 'Riz' existe déjà");
    }

    @Test
    void categorie_filtresEtDesactivation() {
        when(categorieRepository.findByActiveTrue()).thenReturn(List.of(categorie(1L, "Riz", true)));
        when(categorieRepository.findByActiveFalse()).thenReturn(List.of(categorie(2L, "Gaz", false)));
        when(categorieRepository.findAll()).thenReturn(List.of(categorie(1L, "Riz", true), categorie(2L, "Gaz", false)));
        CategorieDepense riz = categorie(1L, "Riz", true);
        when(categorieRepository.findById(1L)).thenReturn(Optional.of(riz));
        when(categorieRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(categorieService.list("active")).hasSize(1);
        assertThat(categorieService.list("INACTIVE")).hasSize(1);
        assertThat(categorieService.list("ALL")).hasSize(2);
        assertThat(categorieService.deactivate(1L).active()).isFalse();
        assertThatThrownBy(() -> categorieService.deactivate(99L)).hasMessage("Catégorie introuvable : 99");
    }

    // ---------------------------------------------------------------- tresorerie

    @Test
    void tresorerie_libellesTotauxEtCategoriesTrieesParNom() {
        when(paiementClient.totauxMensuels(2026, 10)).thenReturn(
                new TotauxPaiementsView(new BigDecimal("110000.00"), new BigDecimal("115000.00")));
        when(recetteRepository.sumByStatusAndRecetteDateBetween(StatutMouvement.VALIDE,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).thenReturn(new BigDecimal("50000.00"));
        when(depenseRepository.sumByCategorieName(StatutMouvement.VALIDE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(List.of(new Object[]{"Riz", new BigDecimal("40000.00")}, new Object[]{"Gaz", new BigDecimal("12000.00")}));

        TresorerieResponse t = tresorerieService.getTresorerie(2026, 10);

        assertThat(t.year()).isEqualTo(2026);
        assertThat(t.month()).isEqualTo(10);
        assertThat(t.detailRecettes()).containsExactly(
                new LigneDetail("Paiements des enfants (inscriptions + mensualités)", new BigDecimal("110000.00")),
                new LigneDetail("Autres recettes (dons, subventions)", new BigDecimal("50000.00")));
        assertThat(t.detailDepenses()).extracting(LigneDetail::source)
                .containsExactly("Salaires du personnel", "Gaz", "Riz");
        assertThat(t.totalRecettes()).isEqualByComparingTo("160000");
        assertThat(t.totalDepenses()).isEqualByComparingTo("167000");
        assertThat(t.solde()).isEqualByComparingTo("-7000");
    }

    @Test
    void tresorerie_moisSansMouvement_soldeNul() {
        when(paiementClient.totauxMensuels(2026, 2)).thenReturn(new TotauxPaiementsView(BigDecimal.ZERO, BigDecimal.ZERO));
        when(recetteRepository.sumByStatusAndRecetteDateBetween(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(depenseRepository.sumByCategorieName(any(), any(), any())).thenReturn(List.of());

        TresorerieResponse t = tresorerieService.getTresorerie(2026, 2);

        assertThat(t.detailDepenses()).extracting(LigneDetail::source).containsExactly("Salaires du personnel");
        assertThat(t.solde()).isEqualByComparingTo("0");
        verify(recetteRepository).sumByStatusAndRecetteDateBetween(StatutMouvement.VALIDE,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
    }

    private static CategorieDepense categorie(Long id, String name, boolean active) {
        CategorieDepense c = new CategorieDepense();
        c.setId(id);
        c.setName(name);
        c.setActive(active);
        return c;
    }
}
