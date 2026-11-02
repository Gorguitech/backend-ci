package com.daara.internatadmin.finance.recette;

import com.daara.internatadmin.common.PrintActionResponse;
import com.daara.internatadmin.finance.recette.dto.AutreRecetteResponse;
import com.daara.internatadmin.finance.recette.dto.CreateAutreRecetteRequest;
import com.daara.internatadmin.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/autres-recettes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AutreRecetteController {

    private final AutreRecetteService autreRecetteService;

    @PostMapping
    public ResponseEntity<AutreRecetteResponse> create(
            @Valid @RequestBody CreateAutreRecetteRequest dto,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(autreRecetteService.create(dto, currentUser.getId(), currentUser.getUsername()));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<AutreRecetteResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(autreRecetteService.cancel(id));
    }

    @GetMapping
    public ResponseEntity<List<AutreRecetteResponse>> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        LocalDate now = LocalDate.now();
        int y = (year != null) ? year : now.getYear();
        int m = (month != null) ? month : now.getMonthValue();
        return ResponseEntity.ok(autreRecetteService.list(y, m));
    }

    @GetMapping("/{id}/receipt-data")
    public ResponseEntity<AutreRecetteResponse> getReceiptData(@PathVariable Long id) {
        return ResponseEntity.ok(autreRecetteService.getReceiptData(id));
    }

    @PostMapping("/{id}/print-receipt")
    public ResponseEntity<PrintActionResponse> recordReceiptPrint(@PathVariable Long id) {
        return ResponseEntity.ok(autreRecetteService.recordReceiptPrint(id));
    }
}