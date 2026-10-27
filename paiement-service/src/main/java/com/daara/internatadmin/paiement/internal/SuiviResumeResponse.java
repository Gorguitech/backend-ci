package com.daara.internatadmin.paiement.internal;

/**
 * Nombre d'eleves et d'employes par statut de paiement pour un mois, lu par pilotage-service
 * (statistiques des modules, tableau de bord et notifications).
 */
public record SuiviResumeResponse(
        long mensualitesPayees,
        long mensualitesPartielles,
        long mensualitesImpayees,
        long inscriptionsImpayees,
        long salairesPayes,
        long salairesImpayes
) {
}
