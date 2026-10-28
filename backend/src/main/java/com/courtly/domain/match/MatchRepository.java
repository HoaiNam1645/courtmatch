package com.courtly.domain.match;

import com.courtly.common.enums.MatchStatus;
import java.time.Instant;
import java.util.List;
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
}
