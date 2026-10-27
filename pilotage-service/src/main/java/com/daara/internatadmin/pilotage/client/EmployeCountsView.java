package com.daara.internatadmin.pilotage.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EmployeCountsView(
        @JsonProperty(required = true) long totalActifs,
        @JsonProperty(required = true) long totalEnConge,
        @JsonProperty(required = true) long totalAnciens
) {
}
