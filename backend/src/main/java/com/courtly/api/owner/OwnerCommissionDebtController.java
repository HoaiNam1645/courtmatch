package com.courtly.api.owner;

import com.courtly.api.owner.dto.CommissionDebtResponse;
import com.courtly.service.owner.OwnerCommissionDebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.20 - tong cong no hoa hong voi Courtly. */
@RestController
@RequestMapping("/api/v1/owner/commission-debt")
@RequiredArgsConstructor
public class OwnerCommissionDebtController {

    private final OwnerCommissionDebtService debtService;

    @GetMapping
    public ResponseEntity<CommissionDebtResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(debtService.get(OwnerVenueController.ownerId(jwt)));
    }
}
