package com.courtly.domain.matching;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Truy van bo loc bat buoc truoc khi AI xep hang doi tac (2.2.20 - 2.2.23). */
public interface PartnerSearchRepository extends Repository<PlayerMatchProfile, UUID> {

    @Query(value = """
            WITH candidates AS (
                SELECT u.id AS userId,
                       u.full_name AS fullName,
                       u.avatar_url AS avatarUrl,
                       pp.playing_style AS playingStyle,
                       pp.preferred_play_type AS preferredPlayType,
                       coalesce(ps.rating_score, 1000) AS ratingScore,
                       coalesce(ps.total_matches, 0) AS totalMatches,
                       min(ST_Distance(candidate_location.location,
                                       requester_location.location) / 1000.0) AS distanceKm,
                       min(candidate_slot.start_time) AS availableFrom,
                       max(candidate_slot.end_time) AS availableUntil
                FROM player_preferred_locations requester_location
                JOIN player_preferred_locations candidate_location
                  ON candidate_location.user_id <> requester_location.user_id
                 AND candidate_location.location IS NOT NULL
                JOIN users u ON u.id = candidate_location.user_id
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN roles r ON r.id = ur.role_id AND r.code = 'player'
                JOIN player_profiles pp ON pp.user_id = u.id
                LEFT JOIN player_statistics ps ON ps.user_id = u.id
                JOIN player_availability_slots candidate_slot
                  ON candidate_slot.user_id = u.id
                 AND candidate_slot.day_of_week = :dayOfWeek
                 AND candidate_slot.start_time <= :startTime
                 AND candidate_slot.end_time >= :endTime
                WHERE requester_location.id = :preferredLocationId
                  AND requester_location.user_id = :requesterId
                  AND requester_location.location IS NOT NULL
                  AND u.status = 'active'
                  AND (u.email IS NULL OR lower(u.email) NOT LIKE '%@bwf.example')
                  AND abs(coalesce(ps.rating_score, 1000) - :requesterRating)
                      <= :maxRatingDifference
                  AND ST_DWithin(
                      candidate_location.location,
                      requester_location.location,
                      least(candidate_location.radius_km, requester_location.radius_km) * 1000
                  )
                GROUP BY u.id, u.full_name, u.avatar_url, pp.playing_style,
                         pp.preferred_play_type, ps.rating_score, ps.total_matches
            )
            SELECT userId, fullName, avatarUrl, playingStyle, preferredPlayType,
                   ratingScore, totalMatches, distanceKm, availableFrom, availableUntil
            FROM candidates
            ORDER BY abs(ratingScore - :requesterRating), distanceKm, fullName, userId
            """,
            countQuery = """
                    WITH candidates AS (
                        SELECT u.id
                        FROM player_preferred_locations requester_location
                        JOIN player_preferred_locations candidate_location
                          ON candidate_location.user_id <> requester_location.user_id
                         AND candidate_location.location IS NOT NULL
                        JOIN users u ON u.id = candidate_location.user_id
                        JOIN user_roles ur ON ur.user_id = u.id
                        JOIN roles r ON r.id = ur.role_id AND r.code = 'player'
                        JOIN player_profiles pp ON pp.user_id = u.id
                        LEFT JOIN player_statistics ps ON ps.user_id = u.id
                        JOIN player_availability_slots candidate_slot
                          ON candidate_slot.user_id = u.id
                         AND candidate_slot.day_of_week = :dayOfWeek
                         AND candidate_slot.start_time <= :startTime
                         AND candidate_slot.end_time >= :endTime
                        WHERE requester_location.id = :preferredLocationId
                          AND requester_location.user_id = :requesterId
                          AND requester_location.location IS NOT NULL
                          AND u.status = 'active'
                          AND (u.email IS NULL OR lower(u.email) NOT LIKE '%@bwf.example')
                          AND abs(coalesce(ps.rating_score, 1000) - :requesterRating)
                              <= :maxRatingDifference
                          AND ST_DWithin(
                              candidate_location.location,
                              requester_location.location,
                              least(candidate_location.radius_km, requester_location.radius_km) * 1000
                          )
                        GROUP BY u.id
                    )
                    SELECT count(*) FROM candidates
                    """,
            nativeQuery = true)
    Page<PartnerCandidateRow> searchCandidates(
            @Param("requesterId") UUID requesterId,
            @Param("preferredLocationId") UUID preferredLocationId,
            @Param("requesterRating") BigDecimal requesterRating,
            @Param("maxRatingDifference") BigDecimal maxRatingDifference,
            @Param("dayOfWeek") short dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            Pageable pageable);

    interface PartnerCandidateRow {
        UUID getUserId();
        String getFullName();
        String getAvatarUrl();
        String getPlayingStyle();
        String getPreferredPlayType();
        BigDecimal getRatingScore();
        int getTotalMatches();
        BigDecimal getDistanceKm();
        LocalTime getAvailableFrom();
        LocalTime getAvailableUntil();
    }
}
