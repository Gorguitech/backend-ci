package com.daara.internatadmin.paiement.enfant.dto;

import com.daara.internatadmin.paiement.ModePaiement;
import com.daara.internatadmin.paiement.enfant.TypePaiementEnfant;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** concernedYear et concernedMonth sont exiges pour une mensualite et ignores pour une inscription. */
public record CreatePaiementEnfantRequest(
        @NotNull Long enfantId,
        @NotNull TypePaiementEnfant paymentType,
        @NotNull ModePaiement paymentMethod,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @Min(2000) @Max(2100) Integer concernedYear,
        @Min(1) @Max(12) Integer concernedMonth,
        LocalDate paymentDate,
        boolean confirmPartial
) {
}
