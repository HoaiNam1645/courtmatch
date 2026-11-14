package com.courtly.api.owner;

import com.courtly.api.owner.dto.CommissionDebtResponse;
import com.courtly.api.owner.dto.DebtBookingListResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;
import com.courtly.service.owner.OwnerCommissionDebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.20 - tong cong no hoa hong voi Courtly. */
@Validated
@RestController
@RequestMapping("/api/v1/owner/commission-debt")
@RequiredArgsConstructor
public class OwnerCommissionDebtController {

    private final OwnerCommissionDebtService debtService;

    @GetMapping
    public ResponseEntity<CommissionDebtResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(debtService.get(OwnerVenueController.ownerId(jwt)));
    }

    /** 2.3.21 - cac booking cau thanh cong no. */
    @GetMapping("/bookings")
    public ResponseEntity<DebtBookingListResponse> bookings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID venueId,
            @RequestParam(defaultValue = "all") String bucket,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(debtService.listBookings(OwnerVenueController.ownerId(jwt), venueId, bucket, page, size));
    }
}
