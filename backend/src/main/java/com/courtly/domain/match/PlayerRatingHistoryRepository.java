package com.courtly.domain.match;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PlayerRatingHistoryRepository extends JpaRepository<PlayerRatingHistory, UUID> {

    boolean existsByMatchId(UUID matchId);

    long countByUserId(UUID userId);

    List<PlayerRatingHistory> findByMatchId(UUID matchId);

    Optional<PlayerRatingHistory> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<PlayerRatingHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
