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
                  AND m.excluded_from_rating = false
                GROUP BY mp.user_id, mp.team_no, m.id, m.scheduled_at, mr.winning_team
            )
            SELECT user_id AS userId,
                   count(*) AS totalMatches,
                   count(*) FILTER (WHERE team_no = winning_team) AS totalWins,
                   count(*) FILTER (WHERE team_no <> winning_team) AS totalLosses,
                   avg(scored)::numeric AS avgPointsScored,
                   avg(conceded)::numeric AS avgPointsConceded,
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
              AND m.excluded_from_rating = false
            """, nativeQuery = true)
    List<UUID> findEligiblePlayerIds();

    interface StatisticsAggregate {
        UUID getUserId();
        long getTotalMatches();
        long getTotalWins();
        long getTotalLosses();
        BigDecimal getAvgPointsScored();
        BigDecimal getAvgPointsConceded();
        Instant getLastPlayedAt();
    }
}
