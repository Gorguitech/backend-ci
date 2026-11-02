package com.daara.internatadmin.personnel;

import com.daara.internatadmin.common.PrintActionResponse;
import com.daara.internatadmin.finance.paiementsalaire.PaiementSalaireService;
import com.daara.internatadmin.personnel.dto.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class EmployeController {

    private final EmployeService employeService;
    private final PaiementSalaireService paiementSalaireService;

    @PostMapping
    public ResponseEntity<EmployeResponse> create(
            @Valid @RequestBody CreateEmployeRequest dto,
            @RequestParam(defaultValue = "false") boolean confirmDuplicate
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeService.create(dto, confirmDuplicate));
    }

    @GetMapping
    public ResponseEntity<List<EmployeResponse>> listAll(
            @RequestParam(required = false) StatutEmploye status
    ) {
        List<EmployeResponse> result = (status != null)
                ? employeService.listByStatus(status)
                : employeService.listAll();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    public ResponseEntity<List<EmployeResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(employeService.search(q));
    }
    @GetMapping("/matricule/{matricule}")
    public ResponseEntity<EmployeResponse> getByMatricule(@PathVariable String matricule) {
        return ResponseEntity.ok(employeService.getByMatricule(matricule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmployeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeRequest dto
    ) {
        return ResponseEntity.ok(employeService.update(id, dto));
    }
    @GetMapping("/{id}")
    public ResponseEntity<EmployeResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(employeService.getById(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EmployeResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeStatusRequest dto
    ) {
        return ResponseEntity.ok(employeService.updateStatus(id, dto.status()));
    }

    @PostMapping("/{id}/print-card")
    public ResponseEntity<PrintActionResponse> recordCardPrint(@PathVariable Long id) {
        return ResponseEntity.ok(employeService.recordCardPrint(id));
    }

    @PostMapping("/{id}/print-certificate")
    public ResponseEntity<PrintActionResponse> recordCertificatePrint(@PathVariable Long id) {
        return ResponseEntity.ok(employeService.recordCertificatePrint(id));
    }
    @GetMapping("/stats")
    public ResponseEntity<EmployeStatsResponse> getStats() {
        return ResponseEntity.ok(employeService.getStats(paiementSalaireService));
    }

}