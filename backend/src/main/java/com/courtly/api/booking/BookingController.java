package com.courtly.api.booking;

import com.courtly.api.booking.dto.BookingDetailResponse;
import com.courtly.api.booking.dto.BookingSummaryResponse;
import com.courtly.api.booking.dto.CancelBookingRequest;
import com.courtly.api.booking.dto.CreateBookingRequest;
import com.courtly.common.PageResponse;
import com.courtly.service.booking.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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

/**
 * Don dat san cua chinh nguoi dang dang nhap.
 *
 * <p>Khong nhan userId tu client - lay tu token, nen khong xem hay huy duoc don nguoi khac.
 */
@Validated
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /** 2.1.28 - tao yeu cau dat san. */
    @PostMapping
    public ResponseEntity<BookingDetailResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                        @Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.create(currentUserId(jwt), request));
    }

    /** 2.1.32 - lich su dat san, loc theo tab va tu khoa. */
    @GetMapping
    public ResponseEntity<PageResponse<BookingSummaryResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") String tab,
            @RequestParam(required = false)
            @Size(max = 100, message = "Tu khoa toi da 100 ky tu") String q,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(bookingService.list(currentUserId(jwt), tab, q, page, size));
    }

    /** 2.1.29, 2.1.30 - chi tiet don kem timeline trang thai. */
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingDetailResponse> detail(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID bookingId) {
        return ResponseEntity.ok(bookingService.getDetail(currentUserId(jwt), bookingId));
    }

    /** 2.1.31 - huy dat san. */
    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<BookingDetailResponse> cancel(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable UUID bookingId,
                                                        @Valid @RequestBody CancelBookingRequest request) {
        return ResponseEntity.ok(bookingService.cancel(currentUserId(jwt), bookingId, request));
    }

    private static UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
