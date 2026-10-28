package com.courtly.api.match.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MatchGameRequest(
        @NotNull @Min(1) @Max(3) Integer gameNo,
        @NotNull @Min(0) @Max(30) Integer team1Score,
        @NotNull @Min(0) @Max(30) Integer team2Score) {
}
