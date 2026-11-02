package com.daara.internatadmin.finance.depense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateDepenseRequest(
        @NotNull Long categorieId,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        LocalDate expenseDate,
        String description,
        boolean confirmDuplicate
) {}