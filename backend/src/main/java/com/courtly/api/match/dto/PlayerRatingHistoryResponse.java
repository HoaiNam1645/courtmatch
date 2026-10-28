package com.courtly.api.match.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlayerRatingHistoryResponse(
        UUID id,
        UUID matchId,
        BigDecimal oldRating,
        BigDecimal newRating,
        BigDecimal changeAmount,
        Instant createdAt) {
}
