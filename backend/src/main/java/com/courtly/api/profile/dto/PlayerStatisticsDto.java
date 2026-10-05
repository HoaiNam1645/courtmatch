package com.courtly.api.profile.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** So lieu doc tu bang player_statistics, hien o dau trang /profile (2.1.6). */
public record PlayerStatisticsDto(int totalMatches,
                                  int totalWins,
                                  int totalLosses,
                                  BigDecimal winRate,
                                  BigDecimal ratingScore,
                                  Instant lastPlayedAt) {

    /** Nguoi choi chua co tran nao van phai tra ve so 0, khong tra null. */
    public static PlayerStatisticsDto empty() {
        return new PlayerStatisticsDto(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }
}
