package com.daara.internatadmin.paiement.enfant.dto;

import com.daara.internatadmin.paiement.ModePaiement;
import com.daara.internatadmin.paiement.enfant.TypePaiementEnfant;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Donnees du recu (RecuPaiement du frontend) ; libelleStatut : Paiement complet, Paiement partiel ou Frais d'inscription. */
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
        String libelleStatut
) {
}
