package com.courtly.api.owner;

import com.courtly.api.owner.dto.BookingReconciliationResponse;
import com.courtly.service.owner.OwnerBookingReconciliationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.22 - lich su doi soat hoa hong cua booking. */
@RestController
@RequestMapping("/api/v1/owner/bookings/{bookingId}/reconciliation")
@RequiredArgsConstructor
public class OwnerBookingReconciliationController {

    private final OwnerBookingReconciliationService reconciliationService;

    @GetMapping
    public ResponseEntity<BookingReconciliationResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId) {
        return ResponseEntity.ok(reconciliationService.get(OwnerVenueController.ownerId(jwt), bookingId));
    }
}
