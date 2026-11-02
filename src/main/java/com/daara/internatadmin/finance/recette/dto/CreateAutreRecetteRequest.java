package com.daara.internatadmin.finance.recette.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateAutreRecetteRequest(
        @NotBlank String source,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        LocalDate recetteDate,
        String description,
        boolean confirmDuplicate
) {}