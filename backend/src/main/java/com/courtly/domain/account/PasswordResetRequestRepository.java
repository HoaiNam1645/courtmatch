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
public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, UUID> {

    /**
     * Yeu cau con hieu luc moi nhat cua mot dia chi.
     *
     * <p>Chi ma moi nhat duoc chap nhan: moi lan gui ma moi deu danh dau cac yeu cau cu
     * la da dung, nen thuc te chi co mot ban ghi khop.
     */
    @Query("""
            SELECT r FROM PasswordResetRequest r
            JOIN FETCH r.user
            WHERE r.destination = :destination AND r.usedAt IS NULL
            ORDER BY r.createdAt DESC
            LIMIT 1
            """)
    Optional<PasswordResetRequest> findLatestPending(@Param("destination") String destination);

    /** Nap kem user vi buoc dat mat khau moi chay ngoai phien doc entity ban dau. */
    @Query("SELECT r FROM PasswordResetRequest r JOIN FETCH r.user WHERE r.id = :id")
    Optional<PasswordResetRequest> findWithUserById(@Param("id") UUID id);

    /** Vo hieu hoa moi ma cu cua mot tai khoan. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PasswordResetRequest r SET r.usedAt = :now WHERE r.user.id = :userId AND r.usedAt IS NULL")
    int invalidatePending(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Dung de ghi so lan da gui vao cot resend_count. */
    @Query("SELECT count(r) FROM PasswordResetRequest r WHERE r.destination = :destination AND r.createdAt >= :since")
    long countSentSince(@Param("destination") String destination, @Param("since") Instant since);
}
