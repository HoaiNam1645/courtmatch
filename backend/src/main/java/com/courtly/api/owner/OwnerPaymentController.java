package com.courtly.api.owner;

import com.courtly.api.owner.dto.OwnerPaymentDetailResponse;
import com.courtly.api.owner.dto.OwnerPaymentListResponse;
import com.courtly.api.owner.dto.RejectOwnerPaymentRequest;
import com.courtly.service.owner.OwnerPaymentActionService;
import com.courtly.service.owner.OwnerPaymentService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.16, 2.3.27 - thanh toan truc tiep cua cac san. */
@Validated
@RestController
@RequestMapping("/api/v1/owner/payments")
@RequiredArgsConstructor
public class OwnerPaymentController {

    private final OwnerPaymentService paymentService;
    private final OwnerPaymentActionService actionService;

    @GetMapping
    public ResponseEntity<OwnerPaymentListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID venueId,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(paymentService.list(OwnerVenueController.ownerId(jwt), venueId, status, page, size));
    }

    /** 2.3.27 - chi tiet xac nhan thanh toan cua mot booking. */
    @GetMapping("/{paymentId}")
    public ResponseEntity<OwnerPaymentDetailResponse> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return ResponseEntity.ok(actionService.get(OwnerVenueController.ownerId(jwt), paymentId));
    }

    /** 2.3.27 - xac nhan da thu tien. */
    @PostMapping("/{paymentId}/confirm")
    public ResponseEntity<OwnerPaymentDetailResponse> confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return ResponseEntity.ok(actionService.confirm(OwnerVenueController.ownerId(jwt), paymentId));
    }

    /** 2.3.27 - tu choi bao da thanh toan kem ly do. */
    @PostMapping("/{paymentId}/reject")
    public ResponseEntity<OwnerPaymentDetailResponse> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId,
                                                             @Valid @RequestBody RejectOwnerPaymentRequest request) {
        return ResponseEntity.ok(actionService.reject(OwnerVenueController.ownerId(jwt), paymentId, request));
    }
}
