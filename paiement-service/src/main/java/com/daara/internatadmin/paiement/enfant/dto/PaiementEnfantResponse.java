package com.daara.internatadmin.paiement.enfant.dto;

import com.daara.internatadmin.paiement.ModePaiement;
import com.daara.internatadmin.paiement.StatutPaiement;
import com.daara.internatadmin.paiement.enfant.TypePaiementEnfant;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Paiement d'eleve, identique au monolithe et au modele PaiementEnfant du frontend (paiement.model.ts).
 * Pour une inscription, concernedMonth, balanceAfter, isPartial et expectedAmount sont null.
 */
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
) {
}
