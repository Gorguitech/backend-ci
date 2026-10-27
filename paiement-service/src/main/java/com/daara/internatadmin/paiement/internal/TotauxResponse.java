package com.daara.internatadmin.paiement.internal;

import java.math.BigDecimal;

/**
 * Totaux des paiements valides, lus par comptabilite-service (tresorerie) et pilotage-service (solde global).
 *
 * @param paiementsEnfants inscriptions et mensualites encaissees (par date de paiement pour un mois)
 * @param salaires         salaires verses (par mois concerne pour un mois)
 */
public record TotauxResponse(BigDecimal paiementsEnfants, BigDecimal salaires) {
}
