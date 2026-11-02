package com.daara.internatadmin.finance.categoriedepense;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategorieDepenseRepository extends JpaRepository<CategorieDepense, Long> {
    List<CategorieDepense> findByActiveTrue();
    List<CategorieDepense> findByActiveFalse();
}