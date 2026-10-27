package com.daara.internatadmin.paiement.client;

import java.math.BigDecimal;

/** Champs utiles de la fiche eleve renvoyee par enfant-service (les autres champs sont ignores). */
public record EnfantView(
        Long id,
        String matricule,
        String fullName,
        String guardianName,
        String guardianPhone,
        String status,
        BigDecimal registrationFee,
        BigDecimal monthlyFee
) {
}
