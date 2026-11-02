package com.daara.internatadmin.finance.depense;

import com.daara.internatadmin.finance.StatutMouvement;
import com.daara.internatadmin.finance.categoriedepense.CategorieDepense;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "depenses")
@Getter @Setter @NoArgsConstructor
public class Depense {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id", nullable = false)
    private CategorieDepense categorie;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate = LocalDate.now();

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