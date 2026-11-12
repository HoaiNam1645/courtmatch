package com.courtly.api.review.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PartnerReviewListResponse(
        Player player,
        BigDecimal averageRating,
        long totalReviews,
        List<PartnerReviewResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public record Player(UUID id, String fullName, String avatarUrl) {
    }
}
