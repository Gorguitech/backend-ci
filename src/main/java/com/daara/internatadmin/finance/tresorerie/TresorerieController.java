// src/main/java/com/daara/gestiondaara/finance/tresorerie/TresorerieController.java
package com.daara.internatadmin.finance.tresorerie;

import com.daara.internatadmin.finance.tresorerie.dto.TresorerieResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/tresorerie")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TresorerieController {

    private final TresorerieService tresorerieService;

    @GetMapping
    public ResponseEntity<TresorerieResponse> getTresorerie(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        LocalDate now = LocalDate.now();
        int y = (year != null) ? year : now.getYear();
        int m = (month != null) ? month : now.getMonthValue();
        return ResponseEntity.ok(tresorerieService.getTresorerie(y, m));
    }
}