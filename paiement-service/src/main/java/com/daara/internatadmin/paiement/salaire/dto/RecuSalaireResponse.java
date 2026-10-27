package com.daara.internatadmin.paiement.salaire.dto;

import com.daara.internatadmin.paiement.ModePaiement;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Donnees du bulletin de paiement (RecuSalaire du frontend). */
public record RecuSalaireResponse(
        Long paiementId,
        String employeMatricule,
        String employeFullName,
        String employeFunction,
        ModePaiement paymentMethod,
        BigDecimal amount,
        LocalDate concernedMonth,
        LocalDate paymentDate,
        String registeredByUsername
) {
}
