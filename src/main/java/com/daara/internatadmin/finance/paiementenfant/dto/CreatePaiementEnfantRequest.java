package com.daara.internatadmin.finance.paiementenfant.dto;

import com.daara.internatadmin.finance.paiementenfant.ModePaiement;
import com.daara.internatadmin.finance.paiementenfant.TypePaiementEnfant;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePaiementEnfantRequest(
        @NotNull Long enfantId,
        @NotNull TypePaiementEnfant paymentType,
        @NotNull ModePaiement paymentMethod,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount,
        @Min(2000) @Max(2100) Integer concernedYear,
        @Min(1) @Max(12) Integer concernedMonth,
        LocalDate paymentDate,
        boolean confirmPartial
) {}