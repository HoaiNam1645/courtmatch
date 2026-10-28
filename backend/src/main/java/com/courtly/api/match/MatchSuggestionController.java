package com.courtly.api.match;

import com.courtly.api.match.dto.MatchSuggestionResponse;
import com.courtly.common.PageResponse;
import com.courtly.service.match.MatchSuggestionService;
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
@RequestMapping("/api/v1/matches/suggestions")
@RequiredArgsConstructor
public class MatchSuggestionController {

    private final MatchSuggestionService suggestionService;

    @GetMapping
    public ResponseEntity<PageResponse<MatchSuggestionResponse>> suggestions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(suggestionService.suggestions(
                UUID.fromString(jwt.getSubject()), page, size));
    }
}
