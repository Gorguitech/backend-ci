package com.daara.internatadmin.comptabilite.recette.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** recetteDate absente : date du jour. confirmDuplicate : enregistrer malgre un doublon signale. */
public record CreateAutreRecetteRequest(
        @NotBlank String source,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        LocalDate recetteDate,
        String description,
        boolean confirmDuplicate
) {
}
