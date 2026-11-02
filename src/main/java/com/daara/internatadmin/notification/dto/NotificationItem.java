package com.daara.internatadmin.notification.dto;

public record NotificationItem(
        String type,       // ex: "MENSUALITES_IMPAYEES"
        String severity,   // "INFO", "WARNING", "DANGER"
        String message,
        long count
) {}