package com.daara.internatadmin.finance.paiementsalaire.dto;

import com.daara.internatadmin.finance.paiementenfant.ModePaiement;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record CreatePaiementSalaireRequest(
        @NotNull Long employeId,
        @NotNull ModePaiement paymentMethod,
        @NotNull @Min(2000) @Max(2100) Integer concernedYear,
        @NotNull @Min(1) @Max(12) Integer concernedMonth,
        LocalDate paymentDate
) {}