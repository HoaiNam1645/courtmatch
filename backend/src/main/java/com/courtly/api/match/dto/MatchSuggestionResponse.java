package com.courtly.api.match.dto;

import java.time.Instant;
import java.util.UUID;

public record MatchSuggestionResponse(
        UUID bookingId,
        String bookingCode,
        UUID venueId,
        String venueName,
        UUID courtId,
        String courtName,
        Instant playedAt,
        int durationMinutes) {
}
