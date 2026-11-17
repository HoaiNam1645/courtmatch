package com.courtly.api.doubleteam.dto;

import com.courtly.common.enums.DoubleTeamType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 2.3.76 - mot lan chia doi trong lich su cua nguoi choi: 4 nguoi va phuong an khuyen nghi. */
public record DoubleTeamHistoryItem(
        UUID batchId,
        Instant createdAt,
        DoubleTeamType matchType,
        List<Member> players,
        BigDecimal balanceScore,
        String balanceLabel,
        BigDecimal team1WinProbability,
        String scoringSource) {

    public record Member(UUID id, String fullName, String avatarUrl) {
    }
}
