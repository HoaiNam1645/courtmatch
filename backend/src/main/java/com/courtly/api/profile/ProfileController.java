package com.courtly.api.profile;

import com.courtly.api.profile.dto.ProfileResponse;
import com.courtly.api.profile.dto.UpdatePreferencesRequest;
import com.courtly.api.profile.dto.UpdateProfileRequest;
import com.courtly.service.profile.ProfileService;
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

/**
 * Ho so nguoi choi cua chinh nguoi dang dang nhap.
 *
 * <p>Duong dan dung {@code /me}, khong nhan userId tu client, nen khong the doc
 * hoac sua ho so cua nguoi khac.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /** 2.1.6 - xem ho so nguoi choi. */
    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(profileService.getProfile(currentUserId(jwt)));
    }

    /** 2.1.7 - chinh sua ho so nguoi choi. */
    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(profileService.updateProfile(currentUserId(jwt), request));
    }

    /** 2.1.8, 2.1.9, 2.1.10, 2.1.11, 2.1.12 - man hinh thiet lap choi luu mot lan. */
    @PutMapping("/preferences")
    public ResponseEntity<ProfileResponse> updatePreferences(@AuthenticationPrincipal Jwt jwt,
                                                             @Valid @RequestBody UpdatePreferencesRequest request) {
        return ResponseEntity.ok(profileService.updatePreferences(currentUserId(jwt), request));
    }

    private static UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
