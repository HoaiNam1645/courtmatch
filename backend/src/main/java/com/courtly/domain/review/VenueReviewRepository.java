package com.courtly.domain.review;

import com.courtly.common.enums.ReviewStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface VenueReviewRepository extends JpaRepository<VenueReview, UUID> {

    /** Danh gia hien tren trang chi tiet san (2.1.23), lay kem nguoi viet de khoi N+1. */
    @Query(value = """
            SELECT r FROM VenueReview r
            JOIN FETCH r.user
            WHERE r.venue.id = :venueId AND r.status = :status
            ORDER BY r.createdAt DESC
            """,
            countQuery = "SELECT count(r) FROM VenueReview r WHERE r.venue.id = :venueId AND r.status = :status")
    Page<VenueReview> findVisibleByVenue(UUID venueId, ReviewStatus status, Pageable pageable);
}
