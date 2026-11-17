package com.courtly.domain.review;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface VenueReviewReplyRepository extends JpaRepository<VenueReviewReply, UUID> {

    @Query("SELECT r FROM VenueReviewReply r WHERE r.review.id = :reviewId")
    Optional<VenueReviewReply> findByReviewId(UUID reviewId);

    /** Cau tra loi cua nhieu danh gia cung luc (danh sach danh gia cua mot trang). */
    @Query("SELECT r FROM VenueReviewReply r WHERE r.review.id IN :reviewIds")
    List<VenueReviewReply> findByReviewIds(Collection<UUID> reviewIds);
}
