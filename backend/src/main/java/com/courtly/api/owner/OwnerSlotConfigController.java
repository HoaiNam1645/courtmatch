package com.courtly.api.owner;

import com.courtly.api.owner.dto.SlotConfigResponse;
import com.courtly.api.owner.dto.UpdateSlotStepRequest;
import com.courtly.service.owner.OwnerSlotConfigService;
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

/** 2.3.9 - cau hinh khung gio dat san. */
@RestController
@RequestMapping("/api/v1/owner/venues/{venueId}/slot-config")
@RequiredArgsConstructor
public class OwnerSlotConfigController {

    private final OwnerSlotConfigService slotConfigService;

    @GetMapping
    public ResponseEntity<SlotConfigResponse> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID venueId) {
        return ResponseEntity.ok(slotConfigService.get(OwnerVenueController.ownerId(jwt), venueId));
    }

    @PutMapping
    public ResponseEntity<SlotConfigResponse> update(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID venueId,
                                                     @Valid @RequestBody UpdateSlotStepRequest request) {
        return ResponseEntity.ok(slotConfigService.update(OwnerVenueController.ownerId(jwt), venueId, request.stepMinutes()));
    }
}
