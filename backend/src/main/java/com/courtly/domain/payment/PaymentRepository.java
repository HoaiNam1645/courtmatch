package com.courtly.domain.payment;

import com.courtly.common.enums.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Lay payment kem booking de khong phai truy van them khi dung ra DTO. */
    @Query("select p from Payment p join fetch p.booking b join fetch b.user where p.id = :id")
    Optional<Payment> findDetailById(UUID id);

    Optional<Payment> findFirstByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /**
     * Doi chieu webhook voi payment (2.1.38).
     *
     * <p>Noi dung chuyen khoan la thu duy nhat noi tien voi don, nen tim theo no.
     * Chi lay payment con cho tien de khong ghi de mot don da thanh toan xong.
     */
    @Query("""
            select p from Payment p join fetch p.booking
            where p.transferContent = :transferContent and p.status = :status
            """)
    Optional<Payment> findByTransferContentAndStatus(String transferContent, PaymentStatus status);

    /** Cac payment qua han ma chua ai dong trang thai (2.1.42). */
    @Query("""
            select p from Payment p
            where p.status = com.courtly.common.enums.PaymentStatus.PENDING
              and p.expiredAt is not null and p.expiredAt <= :now
            """)
    List<Payment> findExpiredPending(Instant now);
}
