package com.daara.internatadmin.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param initialPasswordHash hash BCrypt (ADMIN_INITIAL_PASSWORD_HASH), lu seulement a la creation du compte admin */
@ConfigurationProperties(prefix = "internatadmin.admin")
public record AdminAccountProperties(String initialPasswordHash) {
}
