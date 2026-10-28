package com.courtly.domain.match;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PlayerStatisticsRepository extends JpaRepository<PlayerStatistics, UUID> {

    @Query(value = """
            WITH per_match AS (
                SELECT mp.user_id,
                       mp.team_no,
                       m.id AS match_id,
                       m.scheduled_at,
                       mr.winning_team,
                       sum(CASE WHEN mp.team_no = 1 THEN mg.team1_score ELSE mg.team2_score END) AS scored,
                       sum(CASE WHEN mp.team_no = 1 THEN mg.team2_score ELSE mg.team1_score END) AS conceded
                FROM match_players mp
                JOIN matches m ON m.id = mp.match_id
                JOIN match_results mr ON mr.match_id = m.id
                JOIN match_games mg ON mg.match_id = m.id
                WHERE mp.user_id IN (:userIds)
                  AND m.status = 'completed'
                  AND mr.confirmed_at IS NOT NULL
                  AND NOT (m.excluded_from_rating = true
                      AND m.exclusion_reason = 'INVALID_BADMINTON_SCORE_LEGACY')
                GROUP BY mp.user_id, mp.team_no, m.id, m.scheduled_at, mr.winning_team
            )
            SELECT user_id AS userId,
                   count(*) AS totalMatches,
                   count(*) FILTER (WHERE team_no = winning_team) AS totalWins,
                   count(*) FILTER (WHERE team_no <> winning_team) AS totalLosses,
                   avg(scored)::numeric AS avgPointsScored,
                   avg(conceded)::numeric AS avgPointsConceded,
                   avg(scored - conceded)::numeric AS avgPointDifference,
                   stddev_samp(scored - conceded)::numeric AS performanceConsistency,
                   max(scheduled_at) AS lastPlayedAt
            FROM per_match
            GROUP BY user_id
            """, nativeQuery = true)
    List<StatisticsAggregate> aggregateForUsers(@Param("userIds") Collection<UUID> userIds);

    @Query(value = """
            SELECT DISTINCT mp.user_id
            FROM match_players mp
            JOIN matches m ON m.id = mp.match_id
            JOIN match_results mr ON mr.match_id = m.id
            WHERE m.status = 'completed'
              AND mr.confirmed_at IS NOT NULL
              AND NOT (m.excluded_from_rating = true
                  AND m.exclusion_reason = 'INVALID_BADMINTON_SCORE_LEGACY')
            """, nativeQuery = true)
    List<UUID> findEligiblePlayerIds();

    @Query(value = """
            SELECT mp.user_id AS userId,
                   m.id AS matchId,
                   m.match_type AS matchType,
                   m.scheduled_at AS scheduledAt,
                   m.created_at AS matchCreatedAt,
                   (mp.team_no = mr.winning_team) AS won,
                   sum(CASE WHEN mp.team_no = 1 THEN mg.team1_score ELSE mg.team2_score END) AS scored,
                   sum(CASE WHEN mp.team_no = 1 THEN mg.team2_score ELSE mg.team1_score END) AS conceded,
                   count(*) FILTER (WHERE
                       (mp.team_no = 1 AND mg.team1_score > mg.team2_score)
                       OR (mp.team_no = 2 AND mg.team2_score > mg.team1_score)) AS gamesWon,
                   count(*) FILTER (WHERE
                       (mp.team_no = 1 AND mg.team1_score < mg.team2_score)
                       OR (mp.team_no = 2 AND mg.team2_score < mg.team1_score)) AS gamesLost
            FROM match_players mp
            JOIN matches m ON m.id = mp.match_id
            JOIN match_results mr ON mr.match_id = m.id
            JOIN match_games mg ON mg.match_id = m.id
            WHERE mp.user_id IN (:userIds)
              AND m.status = 'completed'
              AND mr.confirmed_at IS NOT NULL
              AND NOT (m.excluded_from_rating = true
                  AND m.exclusion_reason = 'INVALID_BADMINTON_SCORE_LEGACY')
            GROUP BY mp.user_id, mp.team_no, m.id, m.match_type,
                     m.scheduled_at, m.created_at, mr.winning_team
            ORDER BY mp.user_id, m.scheduled_at ASC NULLS FIRST, m.created_at ASC, m.id ASC
            """, nativeQuery = true)
    List<PerformanceMatchRow> findPerformanceRows(@Param("userIds") Collection<UUID> userIds);

    interface StatisticsAggregate {
        UUID getUserId();
        long getTotalMatches();
        long getTotalWins();
        long getTotalLosses();
        BigDecimal getAvgPointsScored();
        BigDecimal getAvgPointsConceded();
        BigDecimal getAvgPointDifference();
        BigDecimal getPerformanceConsistency();
        Instant getLastPlayedAt();
    }

    interface PerformanceMatchRow {
        UUID getUserId();
        UUID getMatchId();
        String getMatchType();
        Instant getScheduledAt();
        Instant getMatchCreatedAt();
        boolean getWon();
        long getScored();
        long getConceded();
        long getGamesWon();
        long getGamesLost();
    }
}
