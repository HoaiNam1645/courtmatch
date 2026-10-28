package com.courtly.domain.match;

import com.courtly.common.enums.MatchStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface MatchRepository extends JpaRepository<Match, UUID> {

    boolean existsByBookingIdAndStatusNot(UUID bookingId, MatchStatus status);

    @Query("""
            SELECT DISTINCT m FROM Match m
            WHERE m.status = com.courtly.common.enums.MatchStatus.SCHEDULED
              AND m.createdAt <= :cutoff
            """)
    List<Match> findAwaitingConfirmationBefore(@Param("cutoff") Instant cutoff);

    @Query("""
            SELECT DISTINCT m FROM Match m
            JOIN m.players p
            WHERE m.status = com.courtly.common.enums.MatchStatus.DISPUTED
              AND p.resultDisputedAt IS NOT NULL
              AND p.resultDisputedAt <= :cutoff
            """)
    List<Match> findDisputedBefore(@Param("cutoff") Instant cutoff);

    @Query("SELECT p.user.id FROM MatchPlayer p WHERE p.match.id = :matchId")
    List<UUID> findParticipantUserIds(@Param("matchId") UUID matchId);

    @Query("""
            SELECT DISTINCT m FROM Match m
            JOIN FETCH m.players p
            JOIN FETCH p.user
            JOIN FETCH m.result r
            WHERE m.id = :matchId
              AND m.status = com.courtly.common.enums.MatchStatus.COMPLETED
              AND r.confirmedAt IS NOT NULL
            """)
    Optional<Match> findConfirmedWithPlayers(@Param("matchId") UUID matchId);

    @Query("""
            SELECT DISTINCT m FROM Match m
            JOIN FETCH m.players p
            JOIN FETCH p.user
            JOIN FETCH m.result r
            WHERE m.status = com.courtly.common.enums.MatchStatus.COMPLETED
              AND r.confirmedAt IS NOT NULL
            ORDER BY m.scheduledAt ASC, m.createdAt ASC
            """)
    List<Match> findAllConfirmedWithPlayers();

    @Query(value = """
            SELECT count(DISTINCT m.id)
            FROM matches m
            JOIN match_results mr ON mr.match_id = m.id AND mr.confirmed_at IS NOT NULL
            JOIN match_players first_player ON first_player.match_id = m.id
            JOIN match_players second_player ON second_player.match_id = m.id
            WHERE m.status = 'completed'
              AND m.excluded_from_rating = false
              AND first_player.user_id = :firstUserId
              AND second_player.user_id = :secondUserId
              AND first_player.team_no <> second_player.team_no
              AND m.scheduled_at >= :windowStart
              AND (m.scheduled_at < :scheduledAt
                   OR (m.scheduled_at = :scheduledAt AND m.created_at < :createdAt))
            """, nativeQuery = true)
    long countPriorRatedMeetings(@Param("firstUserId") UUID firstUserId,
                                 @Param("secondUserId") UUID secondUserId,
                                 @Param("windowStart") Instant windowStart,
                                 @Param("scheduledAt") Instant scheduledAt,
                                 @Param("createdAt") Instant createdAt);

    @Query(value = """
            SELECT count(DISTINCT m.id)
            FROM matches m
            JOIN match_players mp ON mp.match_id = m.id
            JOIN match_results mr ON mr.match_id = m.id AND mr.confirmed_at IS NOT NULL
            WHERE mp.user_id = :userId
              AND m.status = 'completed'
              AND m.excluded_from_rating = true
            """, nativeQuery = true)
    long countExcludedMatchesForUser(@Param("userId") UUID userId);

    @Query("""
            SELECT m.id FROM Match m
            JOIN m.players participant
            WHERE participant.user.id = :userId
              AND m.status IN :statuses
            ORDER BY m.scheduledAt DESC NULLS LAST, m.createdAt DESC
            """)
    Page<UUID> findHistoryIds(@Param("userId") UUID userId,
                              @Param("statuses") Collection<MatchStatus> statuses,
                              Pageable pageable);

    @Query("""
            SELECT m.id FROM Match m
            JOIN m.players participant
            WHERE participant.user.id = :userId
              AND m.status = com.courtly.common.enums.MatchStatus.SCHEDULED
              AND participant.resultConfirmedAt IS NULL
            ORDER BY m.scheduledAt DESC NULLS LAST, m.createdAt DESC
            """)
    Page<UUID> findPendingHistoryIds(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT DISTINCT m FROM Match m
            JOIN FETCH m.players player
            JOIN FETCH player.user
            LEFT JOIN FETCH m.result
            LEFT JOIN FETCH m.venue
            WHERE m.id IN :matchIds
            """)
    List<Match> findHistoryDetails(@Param("matchIds") Collection<UUID> matchIds);
}
