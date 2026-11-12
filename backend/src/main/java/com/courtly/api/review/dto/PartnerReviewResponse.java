package com.courtly.api.review.dto;

import java.time.Instant;
import java.util.UUID;

public record PartnerReviewResponse(
        UUID id,
        UUID matchId,
        short rating,
        String comment,
        Reviewer reviewer,
        Instant createdAt) {

    public record Reviewer(UUID id, String fullName, String avatarUrl) {
    }
}
