package com.courtly.api.match.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateMatchGamesRequest(
        @NotNull @Size(min = 2, max = 3) List<@Valid MatchGameRequest> games) {
}
