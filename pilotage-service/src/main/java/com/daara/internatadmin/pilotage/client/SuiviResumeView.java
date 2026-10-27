package com.daara.internatadmin.pilotage.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Nombre d'eleves et d'employes par statut de paiement pour un mois (suivis du monolithe). */
public record SuiviResumeView(
        @JsonProperty(required = true) long mensualitesPayees,
        @JsonProperty(required = true) long mensualitesPartielles,
        @JsonProperty(required = true) long mensualitesImpayees,
        @JsonProperty(required = true) long inscriptionsImpayees,
        @JsonProperty(required = true) long salairesPayes,
        @JsonProperty(required = true) long salairesImpayes
) {
}
