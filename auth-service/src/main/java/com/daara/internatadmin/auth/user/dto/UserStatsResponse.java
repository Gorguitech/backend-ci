package com.daara.internatadmin.auth.user.dto;

public record UserStatsResponse(
        long totalUtilisateurs,
        long totalActifs,
        long totalDesactives,
        long totalAdmins,
        long totalGestionnaires,
        long comptesEnAttenteChangementMdp
) {
}
