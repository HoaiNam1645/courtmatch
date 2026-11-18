package com.courtly.api.owner;

import com.courtly.api.owner.dto.OwnerDisputeResponses;
import com.courtly.service.dispute.OwnerDisputeService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Chu san xem bao cao ve don thuoc san cua minh (2.3.93 - 2.3.96). */
@Validated
@RestController
@RequestMapping("/api/v1/owner/booking-disputes")
@RequiredArgsConstructor
public class OwnerDisputeController {

    private final OwnerDisputeService disputeService;

    /** 2.3.93 - danh sach bao cao (all, awaiting, answered, closed), loc theo san. */
    @GetMapping
    public ResponseEntity<OwnerDisputeResponses.ListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID venueId,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 50")
            @Max(value = 50, message = "Kich thuoc trang tu 1 den 50") int size) {
        return ResponseEntity.ok(disputeService.list(OwnerVenueController.ownerId(jwt), venueId, status, page, size));
    }

    /** 2.3.94 - chi tiet bao cao kem bang chung cua nguoi choi. */
    @GetMapping("/{disputeId}")
    public ResponseEntity<OwnerDisputeResponses.Detail> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID disputeId) {
        return ResponseEntity.ok(disputeService.get(OwnerVenueController.ownerId(jwt), disputeId));
    }
}
