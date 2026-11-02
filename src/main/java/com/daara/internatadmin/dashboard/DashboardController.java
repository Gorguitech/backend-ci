package com.daara.internatadmin.dashboard;

import com.daara.internatadmin.dashboard.dto.GlobalDashboardResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<GlobalDashboardResponse> getGlobalDashboard() {
        return ResponseEntity.ok(dashboardService.getGlobalDashboard());
    }
}