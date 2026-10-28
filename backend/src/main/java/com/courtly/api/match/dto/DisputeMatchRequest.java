package com.courtly.api.match.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisputeMatchRequest(
        @NotBlank @Size(max = 500) String reason) {
}
