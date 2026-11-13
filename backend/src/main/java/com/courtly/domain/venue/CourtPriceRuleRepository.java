package com.courtly.domain.venue;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtPriceRuleRepository extends JpaRepository<CourtPriceRule, UUID> {

    /** Toan bo bang gia (moi trang thai) cua mot dia diem, lay kem san con trong MOT cau (2.3.5). */
    @Query("""
            SELECT r FROM CourtPriceRule r
            JOIN FETCH r.court c
            WHERE c.venue.id = :venueId
            ORDER BY c.courtCode, r.status, r.dayOfWeek NULLS FIRST, r.startTime
            """)
    List<CourtPriceRule> findAllForVenue(@Param("venueId") UUID venueId);

    /** Quy tac dang hieu luc cua mot san con, dung de kiem tra chong lan (2.3.5). */
    @Query("""
            SELECT r FROM CourtPriceRule r
            WHERE r.court.id = :courtId
              AND r.status = com.courtly.common.enums.ActiveStatus.ACTIVE
            """)
    List<CourtPriceRule> findActiveByCourtId(@Param("courtId") UUID courtId);
}
