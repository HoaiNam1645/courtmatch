package com.courtly.api.match;

import com.courtly.api.match.dto.PlayerPerformanceResponse;
import com.courtly.api.match.dto.PlayerRatingHistoryResponse;
import com.courtly.common.PageResponse;
import com.courtly.service.match.PlayerPerformanceQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/players/me")
@RequiredArgsConstructor
public class PlayerPerformanceController {

    private final PlayerPerformanceQueryService performanceService;

    @GetMapping("/performance")
    public ResponseEntity<PlayerPerformanceResponse> performance(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(performanceService.getCurrentPlayer(currentUserId(jwt)));
    }

    @GetMapping("/rating-history")
    public ResponseEntity<PageResponse<PlayerRatingHistoryResponse>> ratingHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Trang phai bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "So dong phai tu 1 tro len")
            @Max(value = 100, message = "Moi trang toi da 100 dong") int size) {
        return ResponseEntity.ok(performanceService.getRatingHistory(
                currentUserId(jwt), page, size));
    }

    private static UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
