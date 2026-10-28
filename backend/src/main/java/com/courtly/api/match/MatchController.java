package com.courtly.api.match;

import com.courtly.api.match.dto.CreateMatchRequest;
import com.courtly.api.match.dto.DisputeMatchRequest;
import com.courtly.api.match.dto.MatchResponse;
import com.courtly.api.match.dto.MatchHistoryResponse;
import com.courtly.api.match.dto.MatchPlayerOptionResponse;
import com.courtly.api.match.dto.UpdateMatchGamesRequest;
import com.courtly.common.PageResponse;
import com.courtly.service.match.MatchService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @GetMapping
    public ResponseEntity<PageResponse<MatchHistoryResponse>> history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") @Size(max = 30) String state,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(matchService.getHistory(
                currentUserId(jwt), state, page, size));
    }

    /** Danh sach tai khoan co the them vao hai doi khi ghi ket qua. */
    @GetMapping("/participants")
    public ResponseEntity<PageResponse<MatchPlayerOptionResponse>> participants(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(matchService.searchParticipants(currentUserId(jwt), q, page, size));
    }

    @GetMapping("/{matchId}")
    public ResponseEntity<MatchResponse> detail(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID matchId) {
        return ResponseEntity.ok(matchService.getDetail(currentUserId(jwt), matchId));
    }

    @PostMapping
    public ResponseEntity<MatchResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody CreateMatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(matchService.create(currentUserId(jwt), request));
    }

    @PutMapping("/{matchId}/games")
    public ResponseEntity<MatchResponse> updateGames(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID matchId,
                                                     @Valid @RequestBody UpdateMatchGamesRequest request) {
        return ResponseEntity.ok(matchService.updateGames(currentUserId(jwt), matchId, request));
    }

    @PostMapping("/{matchId}/confirm")
    public ResponseEntity<MatchResponse> confirm(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID matchId) {
        return ResponseEntity.ok(matchService.confirm(currentUserId(jwt), matchId));
    }

    @PostMapping("/{matchId}/dispute")
    public ResponseEntity<MatchResponse> dispute(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID matchId,
                                                 @Valid @RequestBody DisputeMatchRequest request) {
        return ResponseEntity.ok(matchService.dispute(currentUserId(jwt), matchId, request));
    }

    @DeleteMapping("/{matchId}")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID matchId) {
        matchService.cancel(currentUserId(jwt), matchId);
        return ResponseEntity.noContent().build();
    }

    private static UUID currentUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
