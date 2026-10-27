package com.daara.internatadmin.comptabilite.tresorerie;

import com.daara.internatadmin.comptabilite.StatutMouvement;
import com.daara.internatadmin.comptabilite.client.PaiementClient;
import com.daara.internatadmin.comptabilite.client.TotauxPaiementsView;
import com.daara.internatadmin.comptabilite.depense.DepenseRepository;
import com.daara.internatadmin.comptabilite.recette.AutreRecetteRepository;
import com.daara.internatadmin.comptabilite.tresorerie.dto.TresorerieResponse;
import com.daara.internatadmin.comptabilite.tresorerie.dto.TresorerieResponse.LigneDetail;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Tresorerie mensuelle, regles et libelles du monolithe :
 * <ul>
 *   <li>recettes : paiements des eleves (par date de paiement, lus dans paiement-service) et autres recettes ;</li>
 *   <li>depenses : salaires (par mois concerne, lus dans paiement-service), puis une ligne par categorie de depense,
 *       triees par nom.</li>
 * </ul>
 * paiement-service indisponible : 503 (aucun total partiel n'est renvoye).
 */
@Service
public class TresorerieService {

    static final String LIBELLE_PAIEMENTS_ENFANTS = "Paiements des enfants (inscriptions + mensualités)";
    static final String LIBELLE_AUTRES_RECETTES = "Autres recettes (dons, subventions)";
    static final String LIBELLE_SALAIRES = "Salaires du personnel";

    private final PaiementClient paiementClient;
    private final AutreRecetteRepository autreRecetteRepository;
    private final DepenseRepository depenseRepository;

    public TresorerieService(PaiementClient paiementClient, AutreRecetteRepository autreRecetteRepository,
                             DepenseRepository depenseRepository) {
        this.paiementClient = paiementClient;
        this.autreRecetteRepository = autreRecetteRepository;
        this.depenseRepository = depenseRepository;
    }

    public TresorerieResponse getTresorerie(int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        TotauxPaiementsView paiements = paiementClient.totauxMensuels(year, month);

        BigDecimal autresRecettes = autreRecetteRepository.sumByStatusAndRecetteDateBetween(
                StatutMouvement.VALIDE, ym.atDay(1), ym.atEndOfMonth());
        List<LigneDetail> detailRecettes = List.of(
                new LigneDetail(LIBELLE_PAIEMENTS_ENFANTS, paiements.paiementsEnfants()),
                new LigneDetail(LIBELLE_AUTRES_RECETTES, autresRecettes));
        BigDecimal totalRecettes = paiements.paiementsEnfants().add(autresRecettes);

        List<LigneDetail> parCategorie = depenseRepository
                .sumByCategorieName(StatutMouvement.VALIDE, ym.atDay(1), ym.atEndOfMonth()).stream()
                .map(row -> new LigneDetail((String) row[0], (BigDecimal) row[1]))
                .sorted(Comparator.comparing(LigneDetail::source))
                .toList();
        BigDecimal totalDepensesCourantes = parCategorie.stream()
                .map(LigneDetail::montant).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LigneDetail> detailDepenses = new ArrayList<>();
        detailDepenses.add(new LigneDetail(LIBELLE_SALAIRES, paiements.salaires()));
        detailDepenses.addAll(parCategorie);
        BigDecimal totalDepenses = paiements.salaires().add(totalDepensesCourantes);

        return new TresorerieResponse(year, month, totalRecettes, totalDepenses, totalRecettes.subtract(totalDepenses),
                detailRecettes, detailDepenses);
    }
}
