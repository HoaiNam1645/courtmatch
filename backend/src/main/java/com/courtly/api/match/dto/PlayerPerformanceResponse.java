package com.courtly.api.match.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Du lieu 2.2.4-2.2.7 da xu ly san de giao dien nguoi choi hien thi. */
public record PlayerPerformanceResponse(
        Summary summary,
        Features features,
        DataQuality dataQuality) {

    public record Summary(
            BigDecimal ratingScore,
            String ratingLabel,
            boolean isProvisional,
            int totalMatches,
            int totalWins,
            int totalLosses,
            BigDecimal rawWinRate,
            BigDecimal smoothedWinRate,
            Instant lastPlayedAt) {
    }

    public record Features(
            BigDecimal skillScore,
            BigDecimal smoothedWinRate,
            int totalMatches,
            BigDecimal averagePointDifference,
            BigDecimal performanceConsistency,
            Integer dataRecencyDays) {
    }

    public record DataQuality(
            boolean trainingEligible,
            int minimumTrainingMatches,
            long excludedMatches) {
    }
}
