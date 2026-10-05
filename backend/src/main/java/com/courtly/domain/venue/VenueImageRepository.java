package com.courtly.domain.venue;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VenueImageRepository extends JpaRepository<VenueImage, UUID> {

    List<VenueImage> findAllByVenueIdOrderByDisplayOrderAsc(UUID venueId);

    /**
     * Anh bia cua nhieu dia diem trong MOT cau truy van.
     *
     * <p>Dung cho danh sach don dat san: neu lay tung anh cho tung dong thi thanh N+1.
     */
    @Query(value = """
            SELECT DISTINCT ON (i.venue_id) i.venue_id AS "venueId", i.image_url AS "imageUrl"
            FROM venue_images i
            WHERE i.venue_id IN :venueIds
            ORDER BY i.venue_id, i.is_cover DESC, i.display_order
            """, nativeQuery = true)
    List<VenueCoverView> findCoversByVenueIds(@Param("venueIds") Collection<UUID> venueIds);

    /** Ket qua cua {@link #findCoversByVenueIds}. */
    interface VenueCoverView {
        UUID getVenueId();

        String getImageUrl();
    }
}
