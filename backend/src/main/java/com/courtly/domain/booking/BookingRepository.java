package com.courtly.domain.booking;

import com.courtly.common.enums.BookingStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByBookingCode(String bookingCode);

    /**
     * Lich su dat san cua mot nguoi, loc theo tab va tu khoa (2.1.32).
     *
     * <p>Lay kem venue trong cung cau de danh sach khong sinh mot truy van cho moi don.
     * Tim theo ma don hoac ten san, khong phan biet dau giong tim kiem san.
     *
     * @param keyword luon la mot chuoi LIKE, dung "%" khi khong loc. Khong dung NULL vi
     *                PostgreSQL khong suy duoc kieu cua tham so NULL trong lower().
     */
    @Query(value = """
            SELECT b FROM Booking b
            JOIN FETCH b.venue v
            WHERE b.user.id = :userId
              AND b.status IN :statuses
              AND (lower(b.bookingCode) LIKE lower(:keyword)
                   OR lower(function('courtly_unaccent', v.name))
                      LIKE lower(function('courtly_unaccent', :keyword)))
            ORDER BY b.createdAt DESC
            """,
            countQuery = """
                    SELECT count(b) FROM Booking b
                    WHERE b.user.id = :userId
                      AND b.status IN :statuses
                      AND (lower(b.bookingCode) LIKE lower(:keyword)
                           OR lower(function('courtly_unaccent', b.venue.name))
                              LIKE lower(function('courtly_unaccent', :keyword)))
                    """)
    Page<Booking> findForUser(@Param("userId") UUID userId,
                              @Param("statuses") Collection<BookingStatus> statuses,
                              @Param("keyword") String keyword,
                              Pageable pageable);

    /** Don cho thanh toan da qua han, dung cho job tu dong huy (2.1.42). */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.status = com.courtly.common.enums.BookingStatus.PENDING_PAYMENT
              AND b.expiresAt IS NOT NULL
              AND b.expiresAt < :now
            """)
    List<Booking> findExpiredPendingPayment(@Param("now") Instant now);

    /** Dem don tao trong ngay, dung de sinh so thu tu trong ma don. */
    @Query("""
            SELECT count(b) FROM Booking b
            WHERE b.createdAt >= :dayStart AND b.createdAt < :dayEnd
            """)
    long countCreatedBetween(@Param("dayStart") Instant dayStart, @Param("dayEnd") Instant dayEnd);

    boolean existsByBookingCode(String bookingCode);
}
