package com.courtly.api.notification;

import com.courtly.api.notification.dto.NotificationResponses;
import com.courtly.api.notification.dto.UpdatePreferencesRequest;
import com.courtly.service.notification.NotificationPreferenceService;
import com.courtly.service.notification.NotificationQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.61 - lich su thong bao; 2.3.62 - tuy chon nhan thong bao. Moi vai tro, chi du lieu cua chinh minh. */
@Validated
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryService queryService;
    private final NotificationPreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<NotificationResponses.ListResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(queryService.list(userId(jwt), type, unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<NotificationResponses.UnreadCount> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(queryService.unreadCount(userId(jwt)));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponses.Item> markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID notificationId) {
        return ResponseEntity.ok(queryService.markRead(userId(jwt), notificationId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<NotificationResponses.MarkAllResult> markAllRead(@AuthenticationPrincipal Jwt jwt,
                                                                           @RequestParam(defaultValue = "all") String type) {
        return ResponseEntity.ok(queryService.markAllRead(userId(jwt), type));
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationResponses.PreferenceList> preferences(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(preferenceService.get(userId(jwt)));
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationResponses.PreferenceList> updatePreferences(@AuthenticationPrincipal Jwt jwt,
                                                                                  @Valid @RequestBody UpdatePreferencesRequest request) {
        return ResponseEntity.ok(preferenceService.update(userId(jwt), request));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
