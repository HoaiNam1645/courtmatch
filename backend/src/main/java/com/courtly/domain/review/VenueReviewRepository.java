package com.courtly.domain.review;

import com.courtly.common.enums.ReviewStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Nhu {@link #findVisibleByVenue} nhung loc theo so sao; {@code rating} = 0 la khong loc. */
    @Query(value = """
            SELECT r FROM VenueReview r
            JOIN FETCH r.user
            WHERE r.venue.id = :venueId AND r.status = :status AND (:rating = 0 OR r.rating = :rating)
            ORDER BY r.createdAt DESC, r.id
            """,
            countQuery = """
            SELECT count(r) FROM VenueReview r
            WHERE r.venue.id = :venueId AND r.status = :status AND (:rating = 0 OR r.rating = :rating)
            """)
    Page<VenueReview> findVisibleByVenueAndRating(UUID venueId, ReviewStatus status, int rating, Pageable pageable);

    /** Moi dong la {so sao, so danh gia}. */
    @Query("SELECT r.rating, count(r) FROM VenueReview r WHERE r.venue.id = :venueId AND r.status = :status GROUP BY r.rating")
    List<Object[]> countByRating(UUID venueId, ReviewStatus status);

    /** Danh gia cua nguoi choi cho mot don (moi don toi da mot danh gia, moi trang thai). */
    @Query("SELECT r FROM VenueReview r WHERE r.booking.id = :bookingId AND r.user.id = :userId")
    Optional<VenueReview> findByBookingAndUser(UUID bookingId, UUID userId);

    /** Danh gia cua nguoi choi cho nhieu don cung luc (lich su dat san). */
    @Query("SELECT r FROM VenueReview r WHERE r.user.id = :userId AND r.booking.id IN :bookingIds")
    List<VenueReview> findByUserAndBookings(UUID userId, Collection<UUID> bookingIds);

    @Query("SELECT r FROM VenueReview r JOIN FETCH r.venue WHERE r.id = :id AND r.user.id = :userId")
    Optional<VenueReview> findOwnedByUser(UUID id, UUID userId);

    /**
     * Tinh lai diem trung binh va so danh gia cua san tu cac danh gia dang hien thi.
     * Luon tinh lai tu du lieu goc thay vi cong don de khong lech khi sua/xoa.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE venues SET
                average_rating = COALESCE((SELECT round(avg(rating), 2) FROM venue_reviews
                                           WHERE venue_id = :venueId AND status = 'visible'), 0),
                review_count = (SELECT count(*) FROM venue_reviews WHERE venue_id = :venueId AND status = 'visible')
            WHERE id = :venueId
            """, nativeQuery = true)
    void recalculateVenueRating(UUID venueId);
}
