package com.daara.internatadmin.audit;

import com.daara.internatadmin.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLog>> getHistory(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 20, sort = "actionDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(auditService.search(entityType, entityId, userId, pageable));
    }

    @GetMapping("/me")
    public ResponseEntity<Page<AuditLog>> getMyRecentActivity(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PageableDefault(size = 10, sort = "actionDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(auditService.search(null, null, currentUser.getId(), pageable));
    }
}