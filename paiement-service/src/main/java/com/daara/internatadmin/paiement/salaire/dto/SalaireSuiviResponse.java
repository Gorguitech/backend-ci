package com.daara.internatadmin.paiement.salaire.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ligne du suivi des salaires (SalaireSuivi du frontend) ; paymentDate et paiementId null si IMPAYE. */
public record SalaireSuiviResponse(
        Long employeId,
        String matricule,
        String fullName,
        BigDecimal montantAttendu,
        String statut,
        LocalDate paymentDate,
        Long paiementId
) {
}
