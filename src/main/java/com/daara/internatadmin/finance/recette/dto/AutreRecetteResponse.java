package com.daara.internatadmin.finance.recette.dto;

import com.daara.internatadmin.finance.StatutMouvement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AutreRecetteResponse(
        Long id,
        String source,
        BigDecimal amount,
        LocalDate recetteDate,
        String description,
        String registeredByUsername,
        StatutMouvement status
) {}