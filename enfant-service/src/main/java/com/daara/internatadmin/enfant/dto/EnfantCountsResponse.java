package com.daara.internatadmin.enfant.dto;

/** Effectifs lus par pilotage-service (statistiques du module eleves et tableau de bord). */
public record EnfantCountsResponse(long totalActifs, long totalSortis, long totalEnVacances, long nouveauxDuMois) {
}
