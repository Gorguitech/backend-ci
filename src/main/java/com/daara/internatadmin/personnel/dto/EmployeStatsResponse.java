package com.daara.internatadmin.personnel.dto;

public record EmployeStatsResponse(
        long totalActifs,
        long totalEnConge,
        long totalAnciens,
        long salairesPayesCeMois,
        long salairesRestants
) {}