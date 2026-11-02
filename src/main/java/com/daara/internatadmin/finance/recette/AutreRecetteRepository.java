package com.daara.internatadmin.finance.recette;

import com.daara.internatadmin.finance.StatutMouvement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AutreRecetteRepository extends JpaRepository<AutreRecette, Long> {
    List<AutreRecette> findByStatusAndRecetteDateBetween(StatutMouvement status, LocalDate start, LocalDate end);


    boolean existsBySourceAndAmountAndRecetteDateAndRegisteredByUserIdAndCreatedAtAfter(
            String source, BigDecimal amount, LocalDate recetteDate, Long registeredByUserId, LocalDateTime after
    );
    List<AutreRecette> findByStatus(StatutMouvement status);
}