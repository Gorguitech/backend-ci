package com.daara.internatadmin.enfant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "enfants")
@Getter @Setter @NoArgsConstructor
public class Enfant {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false, length = 20)
    private String matricule;
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "approximate_age")
    private Integer approximateAge;

    @Column(nullable = false, length = 1)
    private String gender; // "M" ou "F"

    @Column(name = "guardian_name", nullable = false)
    private String guardianName;

    @Column(name = "guardian_phone", nullable = false)
    private String guardianPhone;

    private String neighborhood;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    private StatutEnfant status = StatutEnfant.ACTIF;

    @Column(name = "room_group")
    private String roomGroup;

    @Column(name = "registration_fee", nullable = false)
    private BigDecimal registrationFee;

    @Column(name = "monthly_fee", nullable = false)
    private BigDecimal monthlyFee;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public String getFullName() {
        return firstName + " " + lastName;
    }
}