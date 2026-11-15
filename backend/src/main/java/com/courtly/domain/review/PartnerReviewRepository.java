package com.courtly.domain.review;

import com.courtly.common.enums.ReviewStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PartnerReviewRepository extends JpaRepository<PartnerReview, UUID> {

    boolean existsByReviewerIdAndReviewedUserIdAndMatchId(
            UUID reviewerId, UUID reviewedUserId, UUID matchId);

    @Query("""
            SELECT review.reviewedUser.id FROM PartnerReview review
            WHERE review.reviewer.id = :reviewerId
              AND review.match.id = :matchId
            """)
    List<UUID> findReviewedUserIds(@Param("reviewerId") UUID reviewerId,
                                   @Param("matchId") UUID matchId);

    @EntityGraph(attributePaths = "reviewer")
    Page<PartnerReview> findByReviewedUserIdAndStatusOrderByCreatedAtDesc(
            UUID reviewedUserId, ReviewStatus status, Pageable pageable);

    long countByReviewedUserIdAndStatus(UUID reviewedUserId, ReviewStatus status);

    @Query("""
            SELECT AVG(review.rating) FROM PartnerReview review
            WHERE review.reviewedUser.id = :reviewedUserId
              AND review.status = :status
            """)
    Double averageRating(@Param("reviewedUserId") UUID reviewedUserId,
                         @Param("status") ReviewStatus status);

    /** Nap san nguoi danh gia va nguoi duoc danh gia de man quan tri khong bi N+1. */
    @Query("""
            SELECT review FROM PartnerReview review
            JOIN FETCH review.reviewer JOIN FETCH review.reviewedUser
            WHERE review.id IN :ids
            """)
    List<PartnerReview> findAllWithPeople(@Param("ids") java.util.Collection<UUID> ids);

    @Query("""
            SELECT review FROM PartnerReview review
            JOIN FETCH review.reviewer JOIN FETCH review.reviewedUser
            WHERE review.id = :id
            """)
    java.util.Optional<PartnerReview> findWithPeopleById(@Param("id") UUID id);
}
