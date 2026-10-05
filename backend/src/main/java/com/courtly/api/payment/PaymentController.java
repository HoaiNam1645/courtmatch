package com.courtly.api.payment;

import com.courtly.api.payment.dto.PaymentDetailResponse;
import com.courtly.service.payment.PaymentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Giao dich thanh toan cua chinh nguoi dang dang nhap.
 *
 * <p>Khong co endpoint nao cho phep tu danh dau da thanh toan: chi webhook SePay moi lam duoc
 * dieu do.
 */
@Validated
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 2.1.34 - 2.1.36, 2.1.39: man hinh thanh toan, frontend hoi lai dinh ky de biet da tra chua. */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDetailResponse> detail(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.getDetail(currentUserId(jwt), paymentId));
    }

    /** 2.1.40: tu man hinh dat san thanh cong, tra cuu giao dich theo ma don. */
    @GetMapping("/by-booking/{bookingId}")
    public ResponseEntity<PaymentDetailResponse> byBooking(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID bookingId) {
        return ResponseEntity.ok(paymentService.getByBooking(currentUserId(jwt), bookingId));
    }

    /** 2.1.41, 2.1.42: thu lai sau khi that bai hoac het han, mo lai cua so thanh toan. */
    @PostMapping("/{paymentId}/retry")
    public ResponseEntity<PaymentDetailResponse> retry(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.retry(currentUserId(jwt), paymentId));
    }

    private static UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
