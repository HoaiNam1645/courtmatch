package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminFeeRevenueResponse;
import com.courtly.service.admin.AdminFeeRevenueService;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.42 - xem tong doanh thu phi nen tang. */
@RestController
@RequestMapping("/api/v1/admin/finance")
@RequiredArgsConstructor
public class AdminFeeRevenueController {

    private final AdminFeeRevenueService revenueService;

    @GetMapping("/fee-revenue")
    public ResponseEntity<AdminFeeRevenueResponse> feeRevenue(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String granularity,
            @RequestParam(required = false) UUID venueId) {
        return ResponseEntity.ok(revenueService.get(AdminJwt.adminId(jwt), from, to, granularity, venueId));
    }
}
