package com.daara.internatadmin.personnel.dto;

import com.daara.internatadmin.personnel.StatutEmploye;
import jakarta.validation.constraints.NotNull;

public record UpdateEmployeStatusRequest(@NotNull StatutEmploye status) {}