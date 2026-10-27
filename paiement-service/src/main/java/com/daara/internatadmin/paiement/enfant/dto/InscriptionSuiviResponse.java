package com.daara.internatadmin.paiement.enfant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Ligne du suivi des inscriptions (InscriptionSuivi du frontend) ; paymentDate et paiementId null si IMPAYE. */
public record InscriptionSuiviResponse(
        Long enfantId,
        String matricule,
        String fullName,
        BigDecimal montantAttendu,
        String statut,
        LocalDate paymentDate,
        Long paiementId
) {
}
