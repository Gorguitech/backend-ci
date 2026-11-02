package com.daara.internatadmin.finance.paiementenfant.dto;

import com.daara.internatadmin.finance.paiementenfant.ModePaiement;
import com.daara.internatadmin.finance.paiementenfant.StatutPaiement;
import com.daara.internatadmin.finance.paiementenfant.TypePaiementEnfant;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PaiementEnfantResponse(
        Long id,
        Long enfantId,
        String enfantFullName,
        String enfantMatricule,
        TypePaiementEnfant paymentType,
        ModePaiement paymentMethod,
        BigDecimal amount,
        LocalDate concernedMonth,
        LocalDate paymentDate,
        String registeredByUsername,
        StatutPaiement status,
        BigDecimal balanceAfter,
        Boolean isPartial,
        BigDecimal expectedAmount,
        BigDecimal excessAmount
) {}