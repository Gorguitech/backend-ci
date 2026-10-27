package com.daara.internatadmin.comptabilite.recette;

import com.daara.internatadmin.comptabilite.StatutMouvement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AutreRecetteRepository extends JpaRepository<AutreRecette, Long> {

    List<AutreRecette> findByStatusAndRecetteDateBetween(StatutMouvement status, LocalDate start, LocalDate end);

    boolean existsBySourceAndAmountAndRecetteDateAndRegisteredByUserIdAndCreatedAtAfter(
            String source, BigDecimal amount, LocalDate recetteDate, Long registeredByUserId, LocalDateTime after);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM AutreRecette r "
            + "WHERE r.status = :status AND r.recetteDate BETWEEN :start AND :end")
    BigDecimal sumByStatusAndRecetteDateBetween(@Param("status") StatutMouvement status,
                                                @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM AutreRecette r WHERE r.status = :status")
    BigDecimal sumByStatus(@Param("status") StatutMouvement status);
}
