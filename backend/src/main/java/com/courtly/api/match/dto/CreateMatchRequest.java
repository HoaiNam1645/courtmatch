package com.courtly.api.match.dto;

import com.courtly.common.enums.MatchType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateMatchRequest(
        UUID bookingId,
        UUID venueId,
        @NotNull MatchType matchType,
        @PastOrPresent Instant scheduledAt,
        @NotEmpty @Size(max = 2) List<@NotNull UUID> team1UserIds,
        @NotEmpty @Size(max = 2) List<@NotNull UUID> team2UserIds,
        @NotNull @Size(min = 2, max = 3) List<@Valid MatchGameRequest> games) {
}
