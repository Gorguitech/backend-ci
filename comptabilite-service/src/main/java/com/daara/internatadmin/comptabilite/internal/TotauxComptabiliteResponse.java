package com.daara.internatadmin.comptabilite.internal;

import java.math.BigDecimal;

/** Totaux valides depuis l'origine, lus par pilotage-service pour le solde global. */
public record TotauxComptabiliteResponse(BigDecimal autresRecettes, BigDecimal depenses) {
}
