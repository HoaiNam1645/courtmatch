package com.courtly.api.owner;

import com.courtly.api.owner.dto.OwnerBookingDetailResponse;
import com.courtly.api.owner.dto.OwnerBookingHistoryItem;
import com.courtly.common.PageResponse;
import com.courtly.service.owner.OwnerBookingHistoryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.15 - lich su dat san cua chu san. */
@Validated
@RestController
@RequestMapping("/api/v1/owner/bookings")
@RequiredArgsConstructor
public class OwnerBookingHistoryController {

    private final OwnerBookingHistoryService historyService;

    @GetMapping
    public ResponseEntity<PageResponse<OwnerBookingHistoryItem>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID venueId,
            @RequestParam(defaultValue = "all") String group,
            @RequestParam(required = false) @Size(max = 100, message = "Tu khoa toi da 100 ky tu") String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(historyService.list(OwnerVenueController.ownerId(jwt), venueId, group, q, from, to, page, size));
    }

    @GetMapping("/{bookingId}")
    public ResponseEntity<OwnerBookingDetailResponse> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookingId) {
        return ResponseEntity.ok(historyService.get(OwnerVenueController.ownerId(jwt), bookingId));
    }
}
