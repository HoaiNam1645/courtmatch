package com.courtly.domain.payment;

import com.courtly.common.enums.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Lay payment kem booking de khong phai truy van them khi dung ra DTO. */
    @Query("select p from Payment p join fetch p.booking b join fetch b.user where p.id = :id")
    Optional<Payment> findDetailById(UUID id);

    Optional<Payment> findFirstByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /** Payments that have not been owner-confirmed before their deadline. */
    @Query("""
            select p from Payment p
            where p.status in (com.courtly.common.enums.PaymentStatus.AWAITING_PAYMENT,
                               com.courtly.common.enums.PaymentStatus.REJECTED)
              and p.expiredAt is not null and p.expiredAt <= :now
            """)
    List<Payment> findExpiredAwaiting(Instant now);

    @Query("""
            select p from Payment p join fetch p.booking b join fetch b.user
            where p.recipientOwner.id = :ownerId and p.status = :status
            order by p.createdAt desc
            """)
    List<Payment> findForOwnerByStatus(@Param("ownerId") UUID ownerId,
                                       @Param("status") PaymentStatus status);
}
