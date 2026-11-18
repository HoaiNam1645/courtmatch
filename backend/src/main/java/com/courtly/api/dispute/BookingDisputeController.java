package com.courtly.api.dispute;

import com.courtly.api.dispute.dto.CreateDisputeRequest;
import com.courtly.api.dispute.dto.DisputeCategoryResponse;
import com.courtly.api.dispute.dto.DisputeResponse;
import com.courtly.service.dispute.BookingDisputeService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Nguoi choi bao cao van de ve don dat san (2.3.85, 2.3.86). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingDisputeController {

    private final BookingDisputeService disputeService;

    /** 2.3.86 - danh muc loai bao cao. */
    @GetMapping("/booking-disputes/categories")
    public ResponseEntity<List<DisputeCategoryResponse>> categories() {
        return ResponseEntity.ok(disputeService.categories());
    }

    /** 2.3.85 - tao bao cao ve mot don. */
    @PostMapping("/bookings/{bookingId}/disputes")
    public ResponseEntity<DisputeResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId,
                                                  @Valid @RequestBody CreateDisputeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(disputeService.create(UUID.fromString(jwt.getSubject()), bookingId, request));
    }
}
