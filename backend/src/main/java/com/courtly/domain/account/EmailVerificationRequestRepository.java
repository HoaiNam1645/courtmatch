package com.courtly.domain.account;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailVerificationRequestRepository
        extends JpaRepository<EmailVerificationRequest, UUID> {

    /** Ma con hieu luc moi nhat cua mot dia chi email. */
    @Query("""
            SELECT r FROM EmailVerificationRequest r
            JOIN FETCH r.user
            WHERE r.email = :email AND r.usedAt IS NULL
            ORDER BY r.createdAt DESC
            LIMIT 1
            """)
    Optional<EmailVerificationRequest> findLatestPending(@Param("email") String email);

    /** Vo hieu hoa moi ma cu cua mot tai khoan truoc khi phat ma moi. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE EmailVerificationRequest r SET r.usedAt = :now "
            + "WHERE r.user.id = :userId AND r.usedAt IS NULL")
    int invalidatePending(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Dung de ghi so lan da gui vao cot resend_count. */
    @Query("SELECT count(r) FROM EmailVerificationRequest r "
            + "WHERE r.email = :email AND r.createdAt >= :since")
    long countSentSince(@Param("email") String email, @Param("since") Instant since);
}
