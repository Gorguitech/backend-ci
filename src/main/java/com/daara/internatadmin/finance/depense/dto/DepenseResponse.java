package com.daara.internatadmin.finance.depense.dto;

import com.daara.internatadmin.finance.StatutMouvement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DepenseResponse(
        Long id,
        Long categorieId,
        String categorieName,
        BigDecimal amount,
        LocalDate expenseDate,
        String description,
        String registeredByUsername,
        StatutMouvement status
) {}