package com.courtly.domain.venue;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtBlockRepository extends JpaRepository<CourtBlock, UUID> {

    /**
     * Cac khoang khoa con hieu luc giao voi cua so thoi gian, cho nhieu san con cung luc.
     *
     * <p>Lay mot lan cho ca dia diem thay vi moi san con mot truy van.
     */
    @Query("""
            SELECT b FROM CourtBlock b
            JOIN FETCH b.court
            WHERE b.court.id IN :courtIds
              AND b.status = com.courtly.common.enums.BlockStatus.ACTIVE
              AND b.startTime < :windowEnd
              AND b.endTime > :windowStart
            """)
    List<CourtBlock> findActiveOverlapping(@Param("courtIds") Collection<UUID> courtIds,
                                           @Param("windowStart") Instant windowStart,
                                           @Param("windowEnd") Instant windowEnd);

    /** Khoang khoa dang hieu luc, chua ket thuc, cua mot dia diem - moi nhat truoc (2.3.10). */
    @Query(value = """
            SELECT b FROM CourtBlock b
            JOIN FETCH b.court c
            WHERE c.venue.id = :venueId
              AND b.status = com.courtly.common.enums.BlockStatus.ACTIVE
              AND b.endTime > :now
            ORDER BY b.startTime, c.courtCode
            """,
            countQuery = """
                    SELECT count(b) FROM CourtBlock b
                    WHERE b.court.venue.id = :venueId
                      AND b.status = com.courtly.common.enums.BlockStatus.ACTIVE
                      AND b.endTime > :now
                    """)
    Page<CourtBlock> findUpcomingActiveByVenue(@Param("venueId") UUID venueId,
                                               @Param("now") Instant now,
                                               Pageable pageable);
}
