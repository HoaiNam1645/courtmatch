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

    /** Thanh toan truc tiep gui toi cac san cua chu san, loc theo trang thai (2.3.16). */
    @Query(value = """
            select p from Payment p
            join fetch p.booking b
            join fetch b.venue v
            join fetch b.user
            where v.id in :venueIds and p.status in :statuses
            order by p.createdAt desc
            """,
            countQuery = """
                    select count(p) from Payment p
                    where p.booking.venue.id in :venueIds and p.status in :statuses
                    """)
    org.springframework.data.domain.Page<Payment> findForVenues(
            @Param("venueIds") java.util.Collection<UUID> venueIds,
            @Param("statuses") java.util.Collection<PaymentStatus> statuses,
            org.springframework.data.domain.Pageable pageable);

    /** So thanh toan theo tung trang thai cua cac san (2.3.16). */
    @Query("""
            select p.status as status, count(p) as total from Payment p
            where p.booking.venue.id in :venueIds
            group by p.status
            """)
    List<StatusCount> countByStatusForVenues(@Param("venueIds") java.util.Collection<UUID> venueIds);

    /**
     * Tien chu san da xac nhan thu truc tiep, gom theo ngay (gio Viet Nam) va theo san (2.3.17).
     *
     * <p>Chi tinh payment da duoc chu san xac nhan da thu ({@code confirmed_at}), ke ca sau do bi
     * hoan mot phan / toan phan: so tien hoan duoc tru o doanh thu thuc nhan (2.3.19).
     */
    @Query(value = """
            SELECT to_char(p.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY-MM-DD') AS day,
                   v.id AS venueId,
                   coalesce(sum(p.amount), 0) AS total,
                   count(*) AS bookings
            FROM payments p
            JOIN bookings b ON b.id = p.booking_id
            JOIN venues v ON v.id = b.venue_id
            WHERE v.id IN (:venueIds)
              AND p.confirmed_at IS NOT NULL
              AND p.confirmed_at >= :fromTime AND p.confirmed_at < :toTime
            GROUP BY 1, 2
            """, nativeQuery = true)
    List<RevenueRow> revenueByDayAndVenue(@Param("venueIds") java.util.Collection<UUID> venueIds,
                                          @Param("fromTime") Instant fromTime,
                                          @Param("toTime") Instant toTime);

    interface RevenueRow {
        String getDay();

        UUID getVenueId();

        java.math.BigDecimal getTotal();

        long getBookings();
    }

    interface StatusCount {
        PaymentStatus getStatus();

        long getTotal();
    }

    /** Thanh toan cua nhieu booking trong MOT cau, cho danh sach lich su (2.3.15). */
    @Query("select p from Payment p where p.booking.id in :bookingIds order by p.createdAt asc")
    List<Payment> findByBookingIds(@Param("bookingIds") java.util.Collection<UUID> bookingIds);

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
