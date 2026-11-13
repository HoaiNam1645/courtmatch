package com.courtly.api.owner;

import com.courtly.api.owner.dto.RevenueStatsResponse;
import com.courtly.service.owner.OwnerRevenueService;
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

/** 2.3.17 - thong ke doanh thu. */
@RestController
@RequestMapping("/api/v1/owner/revenue")
@RequiredArgsConstructor
public class OwnerRevenueController {

    private final OwnerRevenueService revenueService;

    @GetMapping
    public ResponseEntity<RevenueStatsResponse> stats(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID venueId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(revenueService.stats(OwnerVenueController.ownerId(jwt), venueId, from, to));
    }
}
