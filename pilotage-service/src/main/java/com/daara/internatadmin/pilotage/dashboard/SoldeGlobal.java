package com.daara.internatadmin.pilotage.dashboard;

import com.daara.internatadmin.pilotage.client.TotauxComptabiliteView;
import com.daara.internatadmin.pilotage.client.TotauxPaiementsView;

import java.math.BigDecimal;

/**
 * Solde global du monolithe (TresorerieService.getSoldeGlobal) : toutes les lignes valides depuis l'origine.
 * Recettes : paiements des eleves et autres recettes ; depenses : salaires et depenses courantes.
 */
public record SoldeGlobal(BigDecimal totalRecettes, BigDecimal totalDepenses, BigDecimal solde) {

    public static SoldeGlobal of(TotauxPaiementsView paiements, TotauxComptabiliteView comptabilite) {
        BigDecimal recettes = paiements.paiementsEnfants().add(comptabilite.autresRecettes());
        BigDecimal depenses = paiements.salaires().add(comptabilite.depenses());
        return new SoldeGlobal(recettes, depenses, recettes.subtract(depenses));
    }
}
