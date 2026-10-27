package com.daara.internatadmin.comptabilite.depense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** expenseDate absente : date du jour. confirmDuplicate : enregistrer malgre un doublon signale. */
public record CreateDepenseRequest(
        @NotNull Long categorieId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        LocalDate expenseDate,
        String description,
        boolean confirmDuplicate
) {
}
