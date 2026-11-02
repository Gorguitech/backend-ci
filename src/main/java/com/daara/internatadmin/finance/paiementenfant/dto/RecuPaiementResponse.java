package com.daara.internatadmin.finance.paiementenfant.dto;

import com.daara.internatadmin.finance.paiementenfant.ModePaiement;
import com.daara.internatadmin.finance.paiementenfant.TypePaiementEnfant;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecuPaiementResponse(
        Long paiementId,
        String enfantMatricule,
        String enfantFullName,
        String guardianName,
        TypePaiementEnfant paymentType,
        ModePaiement paymentMethod,
        BigDecimal amount,
        LocalDate concernedMonth,
        LocalDate paymentDate,
        BigDecimal balanceAfter,
        boolean isPartial,
        String registeredByUsername,
        String libelleStatut // "Paiement complet", "Paiement partiel", "Frais d'inscription"
) {}