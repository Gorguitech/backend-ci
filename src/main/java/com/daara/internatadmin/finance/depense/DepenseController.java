package com.daara.internatadmin.finance.depense;

import com.daara.internatadmin.common.PrintActionResponse;
import com.daara.internatadmin.finance.depense.dto.CreateDepenseRequest;
import com.daara.internatadmin.finance.depense.dto.DepenseResponse;
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
@RequestMapping("/api/depenses")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DepenseController {

    private final DepenseService depenseService;

    @PostMapping
    public ResponseEntity<DepenseResponse> create(
            @Valid @RequestBody CreateDepenseRequest dto,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(depenseService.create(dto, currentUser.getId(), currentUser.getUsername()));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<DepenseResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(depenseService.cancel(id));
    }

    @GetMapping
    public ResponseEntity<List<DepenseResponse>> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Long categorieId
    ) {
        LocalDate now = LocalDate.now();
        int y = (year != null) ? year : now.getYear();
        int m = (month != null) ? month : now.getMonthValue();
        return ResponseEntity.ok(depenseService.list(y, m, categorieId));
    }

    @GetMapping("/{id}/receipt-data")
    public ResponseEntity<DepenseResponse> getReceiptData(@PathVariable Long id) {
        return ResponseEntity.ok(depenseService.getReceiptData(id));
    }

    @PostMapping("/{id}/print-receipt")
    public ResponseEntity<PrintActionResponse> recordReceiptPrint(@PathVariable Long id) {
        return ResponseEntity.ok(depenseService.recordReceiptPrint(id));
    }
}