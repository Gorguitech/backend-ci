package com.daara.internatadmin.personnel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeResponse(
        Long id,
        String matricule,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        String function,
        String contractType,
        BigDecimal baseSalary,
        LocalDate hireDate,
        String status
) {}