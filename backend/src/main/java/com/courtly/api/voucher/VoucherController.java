package com.courtly.api.voucher;

import com.courtly.api.booking.dto.QuoteRequest;
import com.courtly.api.booking.dto.QuoteResponse;
import com.courtly.api.voucher.dto.VoucherPreviewRequest;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.service.booking.QuoteService;
import com.courtly.service.voucher.VoucherService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** B4 - voucher cua nguoi choi: xem voucher dang phat hanh va xem truoc muc giam khi dat san. */
@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;
    private final QuoteService quoteService;

    public record Preview(String code, String name, BigDecimal discountPercent, BigDecimal originalAmount,
                          BigDecimal discountAmount, BigDecimal payableAmount) {
    }

    @GetMapping
    public ResponseEntity<List<VoucherService.View>> mine(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(voucherService.listForPlayer(UUID.fromString(jwt.getSubject())));
    }

    @PostMapping("/preview")
    public ResponseEntity<Preview> preview(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody VoucherPreviewRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        QuoteResponse quote = quoteService.quote(new QuoteRequest(request.courtId(), request.startTime(), request.durationMinutes()));
        if (!quote.available() || quote.totalAmount() == null) {
            throw ApiException.conflict(ErrorCode.BOOKING_SLOT_TAKEN, quote.unavailableReason() == null
                    ? "Khung gio nay chua co gia." : quote.unavailableReason(), Map.of("startTime", "Khung gio khong dat duoc"));
        }
        VoucherService.Application application = voucherService.preview(userId, request.voucherCode(), quote.totalAmount());
        return ResponseEntity.ok(new Preview(application.voucher().getCode(), application.voucher().getName(),
                application.voucher().getDiscountPercent(), application.originalAmount(), application.discountAmount(),
                application.payableAmount()));
    }
}
