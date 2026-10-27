package com.daara.internatadmin.common.api;

/**
 * Accuse de l'impression d'une carte, d'un certificat ou d'un recu, identique au monolithe
 * et a PrintActionResponse du frontend (common.model.ts). L'impression elle-meme est faite par le navigateur.
 */
public record PrintActionResponse(Long id, String message) {
}
