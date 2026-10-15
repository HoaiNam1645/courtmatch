package com.courtly.api.payment;

import com.courtly.api.payment.dto.PaymentDetailResponse;
import com.courtly.api.payment.dto.RejectPaymentRequest;
import com.courtly.api.payment.dto.ReportPaymentRequest;
import com.courtly.service.payment.PaymentService;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Giao dich thanh toan cua chinh nguoi dang dang nhap.
 *
 * <p>Nguoi choi chi bao da thanh toan; booking chi confirmed khi owner xac nhan da thu tien.
 */
@Validated
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** Chi tiet huong dan va trang thai thanh toan truc tiep. */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDetailResponse> detail(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.getDetail(currentUserId(jwt), paymentId));
    }

    /** Tra cuu giao dich moi nhat cua booking. */
    @GetMapping("/by-booking/{bookingId}")
    public ResponseEntity<PaymentDetailResponse> byBooking(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID bookingId) {
        return ResponseEntity.ok(paymentService.getByBooking(currentUserId(jwt), bookingId));
    }

    /** Hang doi payment ma owner can xac nhan. */
    @GetMapping("/owner")
    public ResponseEntity<List<PaymentDetailResponse>> ownerPayments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "payer_reported") String status) {
        return ResponseEntity.ok(paymentService.listForOwner(currentUserId(jwt), status));
    }

    /** Nguoi choi bao da thanh toan cho owner. */
    @PostMapping("/{paymentId}/report-paid")
    public ResponseEntity<PaymentDetailResponse> reportPaid(@AuthenticationPrincipal Jwt jwt,
                                                            @PathVariable UUID paymentId,
                                                            @Valid @RequestBody ReportPaymentRequest request) {
        return ResponseEntity.ok(paymentService.reportPaid(currentUserId(jwt), paymentId, request));
    }

    /** Owner/staff xac nhan da nhan tien. */
    @PostMapping("/{paymentId}/confirm-received")
    public ResponseEntity<PaymentDetailResponse> confirmReceived(@AuthenticationPrincipal Jwt jwt,
                                                                 @PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.confirmReceived(currentUserId(jwt), paymentId));
    }

    /** Owner/staff tu choi bao da thanh toan cua player. */
    @PostMapping("/{paymentId}/reject-report")
    public ResponseEntity<PaymentDetailResponse> rejectReport(@AuthenticationPrincipal Jwt jwt,
                                                              @PathVariable UUID paymentId,
                                                              @Valid @RequestBody RejectPaymentRequest request) {
        return ResponseEntity.ok(paymentService.rejectReport(currentUserId(jwt), paymentId, request));
    }

    private static UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
