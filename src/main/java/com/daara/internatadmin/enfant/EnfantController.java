package com.daara.internatadmin.enfant;

import com.daara.internatadmin.common.PrintActionResponse;
import com.daara.internatadmin.enfant.dto.*;
import com.daara.internatadmin.finance.paiementenfant.PaiementEnfantService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/enfants")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class EnfantController {

    private final EnfantService enfantService;
    private final PaiementEnfantService paiementEnfantService;

    @PostMapping
    public ResponseEntity<EnfantResponse> create(
            @Valid @RequestBody CreateEnfantRequest dto,
            @RequestParam(defaultValue = "false") boolean confirmDuplicate
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(enfantService.create(dto, confirmDuplicate));
    }

    @GetMapping
    public ResponseEntity<List<EnfantResponse>> listAll(
            @RequestParam(required = false) StatutEnfant status
    ) {
        List<EnfantResponse> result = (status != null)
                ? enfantService.listByStatus(status)
                : enfantService.listAll();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    public ResponseEntity<List<EnfantResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(enfantService.search(q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnfantResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(enfantService.getById(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EnfantResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest dto
    ) {
        return ResponseEntity.ok(enfantService.updateStatus(id, dto.status()));
    }
    @PutMapping("/{id}")
    public ResponseEntity<EnfantResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEnfantRequest dto
    ) {
        return ResponseEntity.ok(enfantService.update(id, dto));
    }
    @PostMapping("/{id}/print-card")
    public ResponseEntity<PrintActionResponse> recordCardPrint(@PathVariable Long id) {
        return ResponseEntity.ok(enfantService.recordCardPrint(id));
    }

    @PostMapping("/{id}/print-certificate")
    public ResponseEntity<PrintActionResponse> recordCertificatePrint(@PathVariable Long id) {
        return ResponseEntity.ok(enfantService.recordCertificatePrint(id));
    }

    @GetMapping("/matricule/{matricule}")
    public ResponseEntity<EnfantResponse> getByMatricule(@PathVariable String matricule) {
        return ResponseEntity.ok(enfantService.getByMatricule(matricule));
    }
    @GetMapping("/stats")
    public ResponseEntity<EnfantStatsResponse> getStats() {
        return ResponseEntity.ok(enfantService.getStats(paiementEnfantService));
    }
}