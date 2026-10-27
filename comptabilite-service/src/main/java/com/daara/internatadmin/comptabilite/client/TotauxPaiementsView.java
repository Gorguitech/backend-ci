package com.daara.internatadmin.comptabilite.client;

import java.math.BigDecimal;

/** Totaux renvoyes par paiement-service : paiements des eleves (par date de paiement) et salaires (par mois concerne). */
public record TotauxPaiementsView(BigDecimal paiementsEnfants, BigDecimal salaires) {
}
