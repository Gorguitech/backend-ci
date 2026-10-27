package com.daara.internatadmin.auth.user.dto;

/** temporaryPassword : renvoye une seule fois, jamais stocke en clair. Ne jamais auditer ni journaliser. */
public record CreateUserResponse(String username, String temporaryPassword, String fullName) {
}
