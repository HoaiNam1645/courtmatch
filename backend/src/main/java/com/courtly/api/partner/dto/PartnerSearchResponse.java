package com.courtly.api.partner.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Ung vien da qua bo loc, kem cac diem dac trung do backend tinh. */
public record PartnerSearchResponse(
        UUID userId,
        String fullName,
        String avatarUrl,
        String playingStyle,
        String preferredPlayType,
        BigDecimal ratingScore,
        String ratingLabel,
        int totalMatches,
        boolean provisional,
        BigDecimal ratingGap,
        BigDecimal skillSimilarity,
        BigDecimal distanceKm,
        BigDecimal locationCompatibility,
        BigDecimal timeCompatibility,
        BigDecimal styleCompatibility,
        LocalTime availableFrom,
        LocalTime availableUntil,
        BigDecimal totalScore,
        BigDecimal confidence,
        String confidenceLabel,
        List<String> reasons,
        String scoringSource,
        String sourceLabel,
        UUID listId) {
}
