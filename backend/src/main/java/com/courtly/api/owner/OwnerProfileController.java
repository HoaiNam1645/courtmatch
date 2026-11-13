package com.courtly.api.owner;

import com.courtly.api.owner.dto.OwnerProfileResponse;
import com.courtly.api.owner.dto.UpdateOwnerProfileRequest;
import com.courtly.service.owner.OwnerProfileService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.2 - xem va cap nhat ho so chu san. */
@RestController
@RequestMapping("/api/v1/owner/profile")
@RequiredArgsConstructor
public class OwnerProfileController {

    private final OwnerProfileService ownerProfileService;

    @GetMapping
    public ResponseEntity<OwnerProfileResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ownerProfileService.get(UUID.fromString(jwt.getSubject())));
    }

    @PutMapping
    public ResponseEntity<OwnerProfileResponse> update(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody UpdateOwnerProfileRequest request) {
        return ResponseEntity.ok(ownerProfileService.update(UUID.fromString(jwt.getSubject()), request));
    }
}
