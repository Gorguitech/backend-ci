package com.daara.internatadmin.finance.paiementsalaire;

import com.daara.internatadmin.finance.paiementenfant.ModePaiement;
import com.daara.internatadmin.personnel.Employe;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "paiements_salaires")
@Getter @Setter @NoArgsConstructor
public class PaiementSalaire {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employe_id", nullable = false)
    private Employe employe;

    @Column(name = "registered_by_user_id", nullable = false)
    private Long registeredByUserId;

    @Column(name = "registered_by_username", nullable = false)
    private String registeredByUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private ModePaiement paymentMethod;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "expected_amount", nullable = false)
    private BigDecimal expectedAmount;

    @Column(name = "concerned_month", nullable = false)
    private LocalDate concernedMonth;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    private StatutPaiementSalaire status = StatutPaiementSalaire.VALIDE;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}