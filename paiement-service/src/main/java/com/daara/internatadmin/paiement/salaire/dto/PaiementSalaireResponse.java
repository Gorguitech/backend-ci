package com.daara.internatadmin.paiement.salaire.dto;

import com.daara.internatadmin.paiement.ModePaiement;
import com.daara.internatadmin.paiement.StatutPaiement;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Paiement de salaire, identique au monolithe et au modele PaiementSalaire du frontend. */
public record PaiementSalaireResponse(
        Long id,
        Long employeId,
        String employeFullName,
        String employeMatricule,
        ModePaiement paymentMethod,
        BigDecimal amount,
        LocalDate concernedMonth,
        LocalDate paymentDate,
        String registeredByUsername,
        StatutPaiement status
) {
}
