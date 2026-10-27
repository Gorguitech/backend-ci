package com.daara.internatadmin.pilotage.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Seul champ utilise des statistiques d'auth-service : notification COMPTES_DESACTIVES. */
public record UserStatsView(@JsonProperty(required = true) long totalDesactives) {
}
