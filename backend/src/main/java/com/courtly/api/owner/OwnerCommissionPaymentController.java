package com.courtly.api.owner;

import com.courtly.api.owner.dto.CommissionPaymentResponse;
import com.courtly.api.owner.dto.CommissionPaymentStatusResponse;
import com.courtly.api.owner.dto.CreateCommissionPaymentRequest;
import com.courtly.service.owner.OwnerCommissionPaymentService;
import com.courtly.service.owner.OwnerCommissionPaymentStatusService;
import java.util.UUID;
import jakarta.validation.Valid;
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

/** 2.3.24 - 2.3.26: thanh toan hoa hong cho Courtly. */
@RestController
@RequestMapping("/api/v1/owner/commission-payments")
@RequiredArgsConstructor
public class OwnerCommissionPaymentController {

    private final OwnerCommissionPaymentService paymentService;
    private final OwnerCommissionPaymentStatusService statusService;

    /** 2.3.24 - gui thong tin thanh toan cong no. */
    @PostMapping
    public ResponseEntity<CommissionPaymentResponse> submit(@AuthenticationPrincipal Jwt jwt,
                                                            @Valid @RequestBody CreateCommissionPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.submit(OwnerVenueController.ownerId(jwt), request));
    }

    /** 2.3.25 - trang thai thanh toan cong no. */
    @GetMapping("/status")
    public ResponseEntity<CommissionPaymentStatusResponse> status(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(statusService.get(OwnerVenueController.ownerId(jwt)));
    }

    /** 2.3.25 - trang thai cua mot lan gui thanh toan. */
    @GetMapping("/{paymentId}")
    public ResponseEntity<CommissionPaymentResponse> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return ResponseEntity.ok(statusService.getPayment(OwnerVenueController.ownerId(jwt), paymentId));
    }
}
