package com.courtly.api.doubleteam;

import com.courtly.api.doubleteam.dto.DoubleTeamRequest;
import com.courtly.api.doubleteam.dto.DoubleTeamSuggestionResponse;
import com.courtly.service.matching.DoubleTeamService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API ghep doi doi nam/nu thong minh (2.3.63 - 2.3.69). */
@RestController
@RequestMapping("/api/v1/double-teams")
@RequiredArgsConstructor
public class DoubleTeamController {

    private final DoubleTeamService doubleTeamService;

    /** Chon 4 nguoi choi, nhan cac phuong an chia doi da tinh va luu. */
    @PostMapping("/suggestions")
    public ResponseEntity<DoubleTeamSuggestionResponse> suggest(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DoubleTeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(doubleTeamService.suggest(UUID.fromString(jwt.getSubject()), request));
    }

    /** Xem lai de xuat cua chinh minh. */
    @GetMapping("/suggestions/{batchId}")
    public ResponseEntity<DoubleTeamSuggestionResponse> get(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID batchId) {
        return ResponseEntity.ok(doubleTeamService.get(UUID.fromString(jwt.getSubject()), batchId));
    }
}
