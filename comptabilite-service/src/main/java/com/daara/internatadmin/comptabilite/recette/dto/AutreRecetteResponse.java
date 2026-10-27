package com.daara.internatadmin.comptabilite.recette.dto;

import com.daara.internatadmin.comptabilite.StatutMouvement;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Recette hors paiements des eleves (AutreRecette du frontend), aussi renvoyee comme donnees du recu. */
public record AutreRecetteResponse(
        Long id,
        String source,
        BigDecimal amount,
        LocalDate recetteDate,
        String description,
        String registeredByUsername,
        StatutMouvement status
) {
}
