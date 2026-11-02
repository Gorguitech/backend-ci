package com.daara.internatadmin.finance.paiementenfant;

import com.daara.internatadmin.common.PrintActionResponse;
import com.daara.internatadmin.finance.paiementenfant.dto.*;
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
@RequestMapping("/api/paiements-enfants")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PaiementEnfantController {

    private final PaiementEnfantService paiementEnfantService;

    @PostMapping
    public ResponseEntity<PaiementEnfantResponse> create(
            @Valid @RequestBody CreatePaiementEnfantRequest dto,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        PaiementEnfantResponse response = paiementEnfantService.create(dto, currentUser.getId(), currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<PaiementEnfantResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(paiementEnfantService.cancel(id));
    }
    @GetMapping("/{id}/receipt-data")
    public ResponseEntity<RecuPaiementResponse> getReceiptData(@PathVariable Long id) {
        return ResponseEntity.ok(paiementEnfantService.getReceiptData(id));
    }
    @PostMapping("/{id}/print-receipt")
    public ResponseEntity<PrintActionResponse> recordReceiptPrint(@PathVariable Long id) {
        return ResponseEntity.ok(paiementEnfantService.recordReceiptPrint(id));
    }

    @GetMapping("/enfant/{enfantId}/inscription")
    public ResponseEntity<List<PaiementEnfantResponse>> getHistoriqueInscription(@PathVariable Long enfantId) {
        return ResponseEntity.ok(paiementEnfantService.getHistoriqueInscription(enfantId));
    }

    @GetMapping("/enfant/{enfantId}/mensualites")
    public ResponseEntity<List<PaiementEnfantResponse>> getHistoriqueMensualites(@PathVariable Long enfantId) {
        return ResponseEntity.ok(paiementEnfantService.getHistoriqueMensualites(enfantId));
    }

    @GetMapping("/mensualites")
    public ResponseEntity<List<SuiviMensuelResponse>> getMensualites(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(defaultValue = "ALL") String status
    ) {
        LocalDate now = LocalDate.now();
        int y = (year != null) ? year : now.getYear();
        int m = (month != null) ? month : now.getMonthValue();
        return ResponseEntity.ok(paiementEnfantService.getSuiviMensuel(y, m, status.toUpperCase()));
    }

    @GetMapping("/inscriptions")
    public ResponseEntity<List<InscriptionSuiviResponse>> getInscriptions(
            @RequestParam(defaultValue = "ALL") String status
    ) {
        return ResponseEntity.ok(paiementEnfantService.getInscriptions(status.toUpperCase()));
    }
}