package com.courtly.api.owner;

import com.courtly.api.owner.dto.OwnerVenueReviewResponses;
import com.courtly.service.owner.OwnerVenueReviewService;
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

/** 2.3.81 - chu san xem danh gia san va tra loi cong khai. */
@Validated
@RestController
@RequestMapping("/api/v1/owner/venue-reviews")
@RequiredArgsConstructor
public class OwnerVenueReviewController {

    private final OwnerVenueReviewService reviewService;

    @GetMapping
    public ResponseEntity<OwnerVenueReviewResponses.ListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID venueId,
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(required = false) Integer rating,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 50")
            @Max(value = 50, message = "Kich thuoc trang tu 1 den 50") int size) {
        return ResponseEntity.ok(reviewService.list(OwnerVenueController.ownerId(jwt), venueId, filter, rating, page, size));
    }

    @PostMapping("/{reviewId}/reply")
    public ResponseEntity<OwnerVenueReviewResponses.Item> reply(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID reviewId,
            @Valid @RequestBody OwnerVenueReviewResponses.ReplyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.reply(OwnerVenueController.ownerId(jwt), reviewId, request.content()));
    }
}
