package com.daara.internatadmin.pilotage.stats.dto;

public record EmployeStatsResponse(
        long totalActifs,
        long totalEnConge,
        long totalAnciens,
        long salairesPayesCeMois,
        long salairesRestants
) {
}
