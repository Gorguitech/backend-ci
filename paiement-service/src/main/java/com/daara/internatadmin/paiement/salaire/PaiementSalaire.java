package com.daara.internatadmin.paiement.salaire;

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
@Table(name = "paiements_salaires")
public class PaiementSalaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employe_id", nullable = false)
    private Long employeId;

    // identite de l'employe figee au moment du paiement (personnel-service est la source de verite)
    @Column(name = "employe_matricule", nullable = false)
    private String employeMatricule;

    @Column(name = "employe_full_name", nullable = false)
    private String employeFullName;

    @Column(name = "employe_function", nullable = false)
    private String employeFunction;

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
    private StatutPaiement status = StatutPaiement.VALIDE;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEmployeId() {
        return employeId;
    }

    public void setEmployeId(Long employeId) {
        this.employeId = employeId;
    }

    public String getEmployeMatricule() {
        return employeMatricule;
    }

    public void setEmployeMatricule(String employeMatricule) {
        this.employeMatricule = employeMatricule;
    }

    public String getEmployeFullName() {
        return employeFullName;
    }

    public void setEmployeFullName(String employeFullName) {
        this.employeFullName = employeFullName;
    }

    public String getEmployeFunction() {
        return employeFunction;
    }

    public void setEmployeFunction(String employeFunction) {
        this.employeFunction = employeFunction;
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

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public void setExpectedAmount(BigDecimal expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public LocalDate getConcernedMonth() {
        return concernedMonth;
    }

    public void setConcernedMonth(LocalDate concernedMonth) {
        this.concernedMonth = concernedMonth;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public StatutPaiement getStatus() {
        return status;
    }

    public void setStatus(StatutPaiement status) {
        this.status = status;
    }
}
