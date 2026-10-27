package com.daara.internatadmin.comptabilite.depense;

import com.daara.internatadmin.comptabilite.StatutMouvement;
import com.daara.internatadmin.comptabilite.categorie.CategorieDepense;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "depenses")
public class Depense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_id", nullable = false)
    private CategorieDepense categorie;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    private String description;

    @Column(name = "registered_by_user_id", nullable = false)
    private Long registeredByUserId;

    @Column(name = "registered_by_username", nullable = false)
    private String registeredByUsername;

    @Enumerated(EnumType.STRING)
    private StatutMouvement status = StatutMouvement.VALIDE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CategorieDepense getCategorie() {
        return categorie;
    }

    public void setCategorie(CategorieDepense categorie) {
        this.categorie = categorie;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.expenseDate = expenseDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public StatutMouvement getStatus() {
        return status;
    }

    public void setStatus(StatutMouvement status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
