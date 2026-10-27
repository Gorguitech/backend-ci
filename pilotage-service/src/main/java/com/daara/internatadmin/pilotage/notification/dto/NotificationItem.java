package com.daara.internatadmin.pilotage.notification.dto;

/**
 * @param type     par exemple MENSUALITES_IMPAYEES
 * @param severity INFO, WARNING ou DANGER (valeurs du monolithe)
 */
public record NotificationItem(String type, String severity, String message, long count) {
}
