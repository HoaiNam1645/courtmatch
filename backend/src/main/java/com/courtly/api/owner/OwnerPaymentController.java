package com.courtly.api.owner;

import com.courtly.api.owner.dto.OwnerPaymentListResponse;
import com.courtly.service.owner.OwnerPaymentService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.16 - thanh toan truc tiep cua cac san. */
@Validated
@RestController
@RequestMapping("/api/v1/owner/payments")
@RequiredArgsConstructor
public class OwnerPaymentController {

    private final OwnerPaymentService paymentService;

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
}
