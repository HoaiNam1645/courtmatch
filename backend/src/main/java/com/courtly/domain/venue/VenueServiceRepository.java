package com.courtly.domain.venue;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VenueServiceRepository extends JpaRepository<VenueService, VenueServiceId> {

    /** Lay kem danh muc dich vu de khong sinh mot cau truy van cho moi dong. */
    @Query("""
            SELECT vs FROM VenueService vs
            JOIN FETCH vs.service s
            WHERE vs.venue.id = :venueId
            ORDER BY s.code
            """)
    List<VenueService> findAllWithServiceByVenueId(@Param("venueId") UUID venueId);
}
