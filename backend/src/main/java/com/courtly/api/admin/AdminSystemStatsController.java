package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminSystemStatsResponse;
import com.courtly.service.admin.AdminSystemStatsService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.38 - xem thong ke he thong. */
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class AdminSystemStatsController {

    private final AdminSystemStatsService statsService;

    @GetMapping("/system")
    public ResponseEntity<AdminSystemStatsResponse> system(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String granularity) {
        return ResponseEntity.ok(statsService.get(AdminJwt.adminId(jwt), from, to, granularity));
    }
}
