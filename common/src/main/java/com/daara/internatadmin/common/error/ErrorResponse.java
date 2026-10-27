package com.daara.internatadmin.common.error;

/**
 * Corps d'erreur commun a tous les services, identique a celui du monolithe : {"error": ..., "message": ...}.
 * Le frontend affiche {@code message} tel quel et teste certaines valeurs de {@code error}.
 */
public record ErrorResponse(String error, String message) {
}
