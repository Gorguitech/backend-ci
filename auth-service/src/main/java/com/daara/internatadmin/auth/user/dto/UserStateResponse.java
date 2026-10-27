package com.daara.internatadmin.auth.user.dto;

/** Etat d'un compte, lu par la gateway a chaque requete (blocage immediat, changement de mot de passe). */
public record UserStateResponse(Long id, boolean active, boolean mustChangePassword, String role) {
}
