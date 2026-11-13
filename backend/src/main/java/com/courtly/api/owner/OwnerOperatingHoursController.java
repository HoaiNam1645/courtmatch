package com.courtly.api.owner;

import com.courtly.api.owner.dto.OperatingHoursResponse;
import com.courtly.api.owner.dto.UpdateOperatingHoursRequest;
import com.courtly.service.owner.OwnerOperatingHoursService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.7 - gio hoat dong cua san. */
@RestController
@RequestMapping("/api/v1/owner/venues/{venueId}/operating-hours")
@RequiredArgsConstructor
public class OwnerOperatingHoursController {

    private final OwnerOperatingHoursService hoursService;

    @GetMapping
    public ResponseEntity<OperatingHoursResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID venueId) {
        return ResponseEntity.ok(hoursService.get(OwnerVenueController.ownerId(jwt), venueId));
    }

    /** Thay toan bo gio hoat dong trong tuan. Booking da tao khong bi huy, chi duoc liet ke de canh bao. */
    @PutMapping
    public ResponseEntity<OperatingHoursResponse> update(@AuthenticationPrincipal Jwt jwt,
                                                         @PathVariable UUID venueId,
                                                         @Valid @RequestBody UpdateOperatingHoursRequest request) {
        return ResponseEntity.ok(hoursService.update(OwnerVenueController.ownerId(jwt), venueId, request.days()));
    }
}
