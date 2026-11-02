package com.daara.internatadmin.enfant.dto;

public record EnfantStatsResponse(
        long totalActifs,
        long totalSortis,
        long totalEnVacances,
        long nouveauxCeMois,
        long impayesCeMois
) {}