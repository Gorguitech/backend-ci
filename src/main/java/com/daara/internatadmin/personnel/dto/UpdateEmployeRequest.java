package com.daara.internatadmin.personnel.dto;

import com.daara.internatadmin.personnel.FonctionEmploye;
import com.daara.internatadmin.personnel.TypeContrat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateEmployeRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String phone,
        @NotNull FonctionEmploye function,
        @NotNull TypeContrat contractType,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal baseSalary
) {}