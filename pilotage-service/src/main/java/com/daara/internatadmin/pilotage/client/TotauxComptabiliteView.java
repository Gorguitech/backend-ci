package com.daara.internatadmin.pilotage.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** Totaux de toutes les autres recettes et depenses valides depuis l'origine. */
public record TotauxComptabiliteView(
        @JsonProperty(required = true) BigDecimal autresRecettes,
        @JsonProperty(required = true) BigDecimal depenses
) {
}
