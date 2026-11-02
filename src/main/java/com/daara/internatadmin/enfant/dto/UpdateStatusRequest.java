package com.daara.internatadmin.enfant.dto;

import com.daara.internatadmin.enfant.StatutEnfant;
import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(@NotNull StatutEnfant status) {}