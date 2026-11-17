package com.courtly.domain.matching;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Khoang cach giua cac vi tri choi da ghim cua hai nguoi choi (2.3.65). */
public interface PlayerDistanceRepository extends Repository<PlayerMatchProfile, UUID> {

    /** Khoang cach gan nhat giua hai nguoi va ban kinh lon hon trong hai nguoi; hai cot null khi mot ben chua ghim vi tri. */
    @Query(value = """
            SELECT min(ST_Distance(a.location, b.location)) / 1000.0 AS distanceKm,
                   greatest(max(a.radius_km), max(b.radius_km)) AS radiusKm
            FROM player_preferred_locations a
            JOIN player_preferred_locations b ON b.user_id = :secondId AND b.location IS NOT NULL
            WHERE a.user_id = :firstId AND a.location IS NOT NULL
            """, nativeQuery = true)
    PairDistance nearestDistance(@Param("firstId") UUID firstId, @Param("secondId") UUID secondId);

    interface PairDistance {
        BigDecimal getDistanceKm();

        BigDecimal getRadiusKm();
    }
}
