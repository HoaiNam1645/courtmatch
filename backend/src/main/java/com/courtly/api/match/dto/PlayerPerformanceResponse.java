package com.courtly.api.match.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

/** Du lieu 2.2.4-2.2.7 da xu ly san de giao dien nguoi choi hien thi. */
public record PlayerPerformanceResponse(
        Summary summary,
        Scoring scoring,
        MatchTypes matchTypes,
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
            int currentWinStreak,
            int longestWinStreak,
            Instant lastPlayedAt) {
    }

    public record Scoring(
            BigDecimal averagePointsScored,
            BigDecimal averagePointsConceded,
            BigDecimal averagePointDifference,
            int totalGamesWon,
            int totalGamesLost) {
    }

    public record MatchTypes(
            MatchTypeStatistics single,
            @JsonProperty("double") MatchTypeStatistics doubles,
            MatchTypeStatistics mixedDouble) {
    }

    public record MatchTypeStatistics(
            int totalMatches,
            int totalWins,
            int totalLosses,
            BigDecimal winRate) {
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
