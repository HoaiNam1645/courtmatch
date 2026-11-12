package com.courtly.api.review;

import com.courtly.api.review.dto.CreatePartnerReviewRequest;
import com.courtly.api.review.dto.PartnerReviewListResponse;
import com.courtly.api.review.dto.PartnerReviewResponse;
import com.courtly.service.review.PartnerReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PartnerReviewController {

    private final PartnerReviewService reviewService;

    @PostMapping("/partner-reviews")
    public ResponseEntity<PartnerReviewResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePartnerReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.create(currentUserId(jwt), request));
    }

    @GetMapping("/players/{playerId}/partner-reviews")
    public ResponseEntity<PartnerReviewListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID playerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(reviewService.listVisible(
                currentUserId(jwt), playerId, page, size));
    }

    private static UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
