package com.courtly.api.match.dto;

import com.courtly.common.enums.MatchDataSource;
import com.courtly.common.enums.MatchStatus;
import com.courtly.common.enums.MatchType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        UUID bookingId,
        MatchType matchType,
        MatchStatus status,
        MatchDataSource dataSource,
        Instant scheduledAt,
        Venue venue,
        List<Player> team1,
        List<Player> team2,
        List<Game> games,
        Short winningTeam,
        Instant confirmedAt,
        String note,
        boolean currentUserCanEdit,
        boolean currentUserCanCancel,
        boolean currentUserCanConfirm,
        boolean currentUserCanDispute) {

    public record Venue(UUID id, String name, String address) {
    }

    public record Player(UUID id, String fullName, String avatarUrl, boolean confirmed, boolean disputed) {
    }

    public record Game(int gameNo, int team1Score, int team2Score) {
    }
}
