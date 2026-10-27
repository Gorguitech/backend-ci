package com.daara.internatadmin.personnel.dto;

/** Effectifs lus par pilotage-service (statistiques du module personnel et tableau de bord). */
public record EmployeCountsResponse(long totalActifs, long totalEnConge, long totalAnciens) {
}
