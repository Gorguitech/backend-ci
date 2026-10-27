package com.daara.internatadmin.pilotage.stats.dto;

public record EnfantStatsResponse(
        long totalActifs,
        long totalSortis,
        long totalEnVacances,
        long nouveauxCeMois,
        long impayesCeMois
) {
}
