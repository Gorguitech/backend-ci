package com.daara.internatadmin.personnel;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employes")
@Getter @Setter @NoArgsConstructor
public class Employe {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false, length = 20)
    private String matricule;
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_function", nullable = false)
    private FonctionEmploye function;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false)
    private TypeContrat contractType;

    @Column(name = "base_salary", nullable = false)
    private BigDecimal baseSalary;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    private StatutEmploye status = StatutEmploye.ACTIF;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getFullName() {
        return firstName + " " + lastName;
    }
}