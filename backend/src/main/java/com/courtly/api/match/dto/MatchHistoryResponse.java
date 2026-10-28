package com.courtly.api.match.dto;

import com.courtly.common.enums.MatchDataSource;
import com.courtly.common.enums.MatchStatus;
import com.courtly.common.enums.MatchType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mot dong lich su tran cua nguoi choi hien tai (2.2.9). */
public record MatchHistoryResponse(
        UUID id,
        MatchType matchType,
        MatchStatus status,
        MatchDataSource dataSource,
        Instant scheduledAt,
        Venue venue,
        List<Player> team1,
        List<Player> team2,
        List<Game> games,
        Short winningTeam,
        BigDecimal ratingChange,
        boolean currentUserNeedsConfirmation) {

    public record Venue(UUID id, String name, String address) {
    }

    public record Player(UUID id, String fullName, String avatarUrl,
                         boolean confirmed, boolean disputed) {
    }

    public record Game(int gameNo, int team1Score, int team2Score) {
    }
}
