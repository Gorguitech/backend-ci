package com.daara.internatadmin.finance.recette;

import com.daara.internatadmin.finance.StatutMouvement;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "autres_recettes")
@Getter @Setter @NoArgsConstructor
public class AutreRecette {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "recette_date", nullable = false)
    private LocalDate recetteDate = LocalDate.now();

    private String description;

    @Column(name = "registered_by_user_id", nullable = false)
    private Long registeredByUserId;

    @Column(name = "registered_by_username", nullable = false)
    private String registeredByUsername;

    @Enumerated(EnumType.STRING)
    private StatutMouvement status = StatutMouvement.VALIDE;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}