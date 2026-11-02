package com.daara.internatadmin.finance.paiementenfant;

import com.daara.internatadmin.enfant.Enfant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "paiements_enfants")
@Getter @Setter @NoArgsConstructor
public class PaiementEnfant {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enfant_id", nullable = false)
    private Enfant enfant;

    @Column(name = "registered_by_user_id", nullable = false)
    private Long registeredByUserId;

    @Column(name = "registered_by_username", nullable = false)
    private String registeredByUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private TypePaiementEnfant paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private ModePaiement paymentMethod;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "concerned_month")
    private LocalDate concernedMonth; // toujours normalisé au 1er jour du mois
    @Enumerated(EnumType.STRING)
    private StatutPaiement status = StatutPaiement.VALIDE;

    @Column(name = "balance_after")
    private BigDecimal balanceAfter;
    @Column(name = "expected_amount")
    private BigDecimal expectedAmount;

    @Column(name = "excess_amount")
    private BigDecimal excessAmount = BigDecimal.ZERO;

    @Column(name = "is_partial")
    private Boolean isPartial;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate = LocalDate.now();

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}