package com.courtly.api.doubleteam.dto;

import com.courtly.common.enums.DoubleTeamType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ket qua chia doi: suc manh tung nguoi, tuong thich tung cap va cac phuong an chia (2.3.63 - 2.3.69). */
public record DoubleTeamSuggestionResponse(
        UUID batchId,
        DoubleTeamType matchType,
        String scoringSource,
        String modelVersion,
        Instant createdAt,
        List<Player> players,
        List<Pair> pairs,
        List<Option> options) {

    /** 2.3.64 - suc manh tung nguoi. strength da keo ve muc khoi diem voi nguoi it tran. */
    public record Player(
            UUID id,
            String fullName,
            String avatarUrl,
            String gender,
            BigDecimal ratingScore,
            int totalMatches,
            boolean provisional,
            BigDecimal winRate,
            BigDecimal strength,
            BigDecimal confidence) {
    }

    /** 2.3.65 - tuong thich mot cap; thanh phan null khi thieu du lieu. */
    public record Pair(
            List<UUID> playerIds,
            BigDecimal skillScore,
            BigDecimal styleScore,
            BigDecimal timeScore,
            BigDecimal locationScore,
            BigDecimal totalScore) {
    }

    /** 2.3.67 - mot doi. */
    public record Team(List<UUID> playerIds, BigDecimal strength, BigDecimal compatibility) {
    }

    /** 2.3.66 / 2.3.68 / 2.3.69 - mot cach chia doi. */
    public record Option(
            int rank,
            boolean recommended,
            Team team1,
            Team team2,
            BigDecimal balanceScore,
            String balanceLabel,
            BigDecimal team1WinProbability,
            String reason) {
    }
}
