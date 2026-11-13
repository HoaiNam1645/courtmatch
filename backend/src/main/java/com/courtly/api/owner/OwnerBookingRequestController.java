package com.courtly.api.owner;

import com.courtly.api.owner.dto.BookingRequestResponse;
import com.courtly.service.owner.OwnerBookingRequestService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.12 - xem cac yeu cau dat san. */
@Validated
@RestController
@RequestMapping("/api/v1/owner/booking-requests")
@RequiredArgsConstructor
public class OwnerBookingRequestController {

    private final OwnerBookingRequestService requestService;

    /** 2.3.13 - xac nhan yeu cau: booking sang cho thanh toan. */
    @PostMapping("/{bookingId}/accept")
    public ResponseEntity<BookingRequestResponse.Item> accept(@AuthenticationPrincipal Jwt jwt,
                                                              @PathVariable UUID bookingId) {
        return ResponseEntity.ok(requestService.accept(OwnerVenueController.ownerId(jwt), bookingId));
    }

    @GetMapping
    public ResponseEntity<BookingRequestResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID venueId,
            @RequestParam(defaultValue = "pending_owner_confirmation") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(requestService.list(OwnerVenueController.ownerId(jwt), venueId, status, date, page, size));
    }
}
