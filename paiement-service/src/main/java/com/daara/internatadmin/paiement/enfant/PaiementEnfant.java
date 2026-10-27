package com.daara.internatadmin.paiement.enfant;

import com.daara.internatadmin.paiement.ModePaiement;
import com.daara.internatadmin.paiement.StatutPaiement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "paiements_enfants")
public class PaiementEnfant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enfant_id", nullable = false)
    private Long enfantId;

    // identite de l'eleve figee au moment du paiement (enfant-service est la source de verite)
    @Column(name = "enfant_matricule", nullable = false)
    private String enfantMatricule;

    @Column(name = "enfant_full_name", nullable = false)
    private String enfantFullName;

    @Column(name = "guardian_name", nullable = false)
    private String guardianName;

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

    // toujours le 1er jour du mois ; null pour une inscription
    @Column(name = "concerned_month")
    private LocalDate concernedMonth;

    @Enumerated(EnumType.STRING)
    private StatutPaiement status = StatutPaiement.VALIDE;

    @Column(name = "balance_after")
    private BigDecimal balanceAfter;

    // montant du fige au premier paiement du mois : un changement de tarif ne reecrit jamais l'historique
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEnfantId() {
        return enfantId;
    }

    public void setEnfantId(Long enfantId) {
        this.enfantId = enfantId;
    }

    public String getEnfantMatricule() {
        return enfantMatricule;
    }

    public void setEnfantMatricule(String enfantMatricule) {
        this.enfantMatricule = enfantMatricule;
    }

    public String getEnfantFullName() {
        return enfantFullName;
    }

    public void setEnfantFullName(String enfantFullName) {
        this.enfantFullName = enfantFullName;
    }

    public String getGuardianName() {
        return guardianName;
    }

    public void setGuardianName(String guardianName) {
        this.guardianName = guardianName;
    }

    public Long getRegisteredByUserId() {
        return registeredByUserId;
    }

    public void setRegisteredByUserId(Long registeredByUserId) {
        this.registeredByUserId = registeredByUserId;
    }

    public String getRegisteredByUsername() {
        return registeredByUsername;
    }

    public void setRegisteredByUsername(String registeredByUsername) {
        this.registeredByUsername = registeredByUsername;
    }

    public TypePaiementEnfant getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(TypePaiementEnfant paymentType) {
        this.paymentType = paymentType;
    }

    public ModePaiement getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(ModePaiement paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getConcernedMonth() {
        return concernedMonth;
    }

    public void setConcernedMonth(LocalDate concernedMonth) {
        this.concernedMonth = concernedMonth;
    }

    public StatutPaiement getStatus() {
        return status;
    }

    public void setStatus(StatutPaiement status) {
        this.status = status;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public void setExpectedAmount(BigDecimal expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public BigDecimal getExcessAmount() {
        return excessAmount;
    }

    public void setExcessAmount(BigDecimal excessAmount) {
        this.excessAmount = excessAmount;
    }

    public Boolean getIsPartial() {
        return isPartial;
    }

    public void setIsPartial(Boolean isPartial) {
        this.isPartial = isPartial;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }
}
