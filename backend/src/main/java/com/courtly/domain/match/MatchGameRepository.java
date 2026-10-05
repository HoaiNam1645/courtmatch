package com.courtly.domain.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface MatchGameRepository extends JpaRepository<MatchGame, UUID> {
}
