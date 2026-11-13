package com.courtly.domain.venue;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtRepository extends JpaRepository<Court, UUID> {

    /**
     * San con dang hoat dong kem bang gia, lay trong MOT cau.
     *
     * <p>Neu de Hibernate lazy-load priceRules thi moi san con se sinh them mot cau
     * truy van (N+1).
     */
    @Query("""
            SELECT DISTINCT c FROM Court c
            LEFT JOIN FETCH c.priceRules
            WHERE c.venue.id = :venueId
              AND c.status = com.courtly.common.enums.CourtStatus.ACTIVE
            ORDER BY c.courtCode
            """)
    List<Court> findActiveWithPriceRules(@Param("venueId") UUID venueId);

    /** So san con (moi trang thai) cua nhieu dia diem trong MOT cau (2.3.3). */
    @Query("""
            SELECT c.venue.id AS venueId, count(c) AS courtCount
            FROM Court c
            WHERE c.venue.id IN :venueIds
            GROUP BY c.venue.id
            """)
    List<VenueCourtCount> countByVenueIds(@Param("venueIds") Collection<UUID> venueIds);

    /** Ket qua cua {@link #countByVenueIds}. */
    interface VenueCourtCount {
        UUID getVenueId();

        long getCourtCount();
    }
}
