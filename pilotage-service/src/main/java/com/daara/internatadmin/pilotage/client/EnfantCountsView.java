package com.daara.internatadmin.pilotage.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EnfantCountsView(
        @JsonProperty(required = true) long totalActifs,
        @JsonProperty(required = true) long totalSortis,
        @JsonProperty(required = true) long totalEnVacances,
        @JsonProperty(required = true) long nouveauxDuMois
) {
}
