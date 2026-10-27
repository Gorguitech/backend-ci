package com.daara.internatadmin.enfant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fiche eleve, identique au monolithe et au modele Enfant du frontend (enfant.model.ts).
 * birthDate, approximateAge, neighborhood et roomGroup peuvent etre null.
 */
public record EnfantResponse(
        Long id,
        String matricule,
        String firstName,
        String lastName,
        String fullName,
        LocalDate birthDate,
        Integer approximateAge,
        String gender,
        String guardianName,
        String guardianPhone,
        String neighborhood,
        LocalDate entryDate,
        String status,
        String roomGroup,
        BigDecimal registrationFee,
        BigDecimal monthlyFee
) {
}
