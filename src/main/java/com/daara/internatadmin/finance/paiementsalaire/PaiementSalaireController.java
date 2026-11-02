package com.daara.internatadmin.finance.paiementsalaire;

import com.daara.internatadmin.common.PrintActionResponse;
import com.daara.internatadmin.finance.paiementsalaire.dto.*;
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
@RequestMapping("/api/paiements-salaires")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PaiementSalaireController {

    private final PaiementSalaireService paiementSalaireService;

    @PostMapping
    public ResponseEntity<PaiementSalaireResponse> create(
            @Valid @RequestBody CreatePaiementSalaireRequest dto,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        PaiementSalaireResponse response = paiementSalaireService.create(dto, currentUser.getId(), currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<PaiementSalaireResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(paiementSalaireService.cancel(id));
    }

    @GetMapping("/{id}/receipt-data")
    public ResponseEntity<RecuSalaireResponse> getReceiptData(@PathVariable Long id) {
        return ResponseEntity.ok(paiementSalaireService.getReceiptData(id));
    }

    @PostMapping("/{id}/print-receipt")
    public ResponseEntity<PrintActionResponse> recordReceiptPrint(@PathVariable Long id) {
        return ResponseEntity.ok(paiementSalaireService.recordReceiptPrint(id));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<PaiementSalaireResponse>> getHistorique(@PathVariable Long employeId) {
        return ResponseEntity.ok(paiementSalaireService.getHistorique(employeId));
    }

    @GetMapping
    public ResponseEntity<List<SalaireSuiviResponse>> getSuivi(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(defaultValue = "ALL") String status
    ) {
        LocalDate now = LocalDate.now();
        int y = (year != null) ? year : now.getYear();
        int m = (month != null) ? month : now.getMonthValue();
        return ResponseEntity.ok(paiementSalaireService.getSuiviMensuel(y, m, status.toUpperCase()));
    }
}