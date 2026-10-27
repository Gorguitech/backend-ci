package com.daara.internatadmin.paiement.client;

import java.math.BigDecimal;

/** Champs utiles de la fiche employe renvoyee par personnel-service (les autres champs sont ignores). */
public record EmployeView(
        Long id,
        String matricule,
        String fullName,
        String function,
        String status,
        BigDecimal baseSalary
) {
}
