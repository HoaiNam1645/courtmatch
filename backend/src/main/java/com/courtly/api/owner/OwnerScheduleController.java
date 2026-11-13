package com.courtly.api.owner;

import com.courtly.api.owner.dto.VenueScheduleResponse;
import com.courtly.service.owner.OwnerScheduleService;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.8 - lich san theo ngay / tuan. */
@RestController
@RequestMapping("/api/v1/owner/venues/{venueId}/schedule")
@RequiredArgsConstructor
public class OwnerScheduleController {

    private final OwnerScheduleService scheduleService;

    /**
     * @param date ngay bat dau (gio Viet Nam)
     * @param days 1 = xem theo ngay, 7 = xem theo tuan
     */
    @GetMapping
    public ResponseEntity<VenueScheduleResponse> schedule(@AuthenticationPrincipal Jwt jwt,
                                                          @PathVariable UUID venueId,
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                          @RequestParam(defaultValue = "1") int days) {
        return ResponseEntity.ok(scheduleService.getSchedule(OwnerVenueController.ownerId(jwt), venueId, date, days));
    }
}
