package com.courtly.api.review;

import com.courtly.api.review.dto.MyVenueReviewResponse;
import com.courtly.api.review.dto.VenueReviewRequest;
import com.courtly.service.review.VenueReviewService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Nguoi choi viet, sua, xoa danh gia san cua chinh minh (2.3.77 - 2.3.79). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VenueReviewController {

    private final VenueReviewService reviewService;

    /** 2.3.77 - viet danh gia sau khi choi. */
    @PostMapping("/bookings/{bookingId}/venue-review")
    public ResponseEntity<MyVenueReviewResponse> create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId, @Valid @RequestBody VenueReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.create(UUID.fromString(jwt.getSubject()), bookingId, request));
    }
}
