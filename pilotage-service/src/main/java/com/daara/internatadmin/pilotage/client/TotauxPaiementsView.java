package com.daara.internatadmin.pilotage.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** Totaux de tous les paiements valides depuis l'origine. */
public record TotauxPaiementsView(
        @JsonProperty(required = true) BigDecimal paiementsEnfants,
        @JsonProperty(required = true) BigDecimal salaires
) {
}
