package com.courtly.domain.finance;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingCommissionRepository extends JpaRepository<BookingCommission, UUID> {

    Optional<BookingCommission> findByBookingId(UUID bookingId);

    /** Hoa hong cua cac san, kem don, san, nguoi dat va chinh sach da ap dung (2.3.18). */
    @Query(value = """
            select c from BookingCommission c
            join fetch c.booking b
            join fetch b.venue v
            join fetch b.user
            left join fetch c.feeConfig
            where v.id in :venueIds and c.status in :statuses
              and c.calculatedAt >= :fromTime and c.calculatedAt < :toTime
            order by c.calculatedAt desc
            """,
            countQuery = """
                    select count(c) from BookingCommission c
                    where c.booking.venue.id in :venueIds and c.status in :statuses
                      and c.calculatedAt >= :fromTime and c.calculatedAt < :toTime
                    """)
    org.springframework.data.domain.Page<BookingCommission> findForVenues(
            @org.springframework.data.repository.query.Param("venueIds") java.util.Collection<UUID> venueIds,
            @org.springframework.data.repository.query.Param("statuses") java.util.Collection<String> statuses,
            @org.springframework.data.repository.query.Param("fromTime") java.time.Instant fromTime,
            @org.springframework.data.repository.query.Param("toTime") java.time.Instant toTime,
            org.springframework.data.domain.Pageable pageable);

    /** Tong hoa hong cua tap da loc, khong phan trang (2.3.18). */
    @Query("""
            select coalesce(sum(c.commissionAmount), 0) from BookingCommission c
            where c.booking.venue.id in :venueIds and c.status in :statuses
              and c.calculatedAt >= :fromTime and c.calculatedAt < :toTime
            """)
    BigDecimal sumForVenues(
            @org.springframework.data.repository.query.Param("venueIds") java.util.Collection<UUID> venueIds,
            @org.springframework.data.repository.query.Param("statuses") java.util.Collection<String> statuses,
            @org.springframework.data.repository.query.Param("fromTime") java.time.Instant fromTime,
            @org.springframework.data.repository.query.Param("toTime") java.time.Instant toTime);

    /**
     * Hoa hong cua chu san gom theo san, trang thai va "da qua han hay chua" (2.3.20).
     * Tong hop truc tiep tu booking_commissions, khong co so cai cong no rieng.
     */
    @Query(value = """
            SELECT x.venue_id AS venueId, x.venue_name AS venueName, x.status AS status, x.overdue AS overdue,
                   sum(x.commission_amount) AS total, count(*) AS bookings, min(x.due_at) AS earliestDue
            FROM (
                SELECT v.id AS venue_id, v.name AS venue_name, bc.status AS status, bc.commission_amount AS commission_amount,
                       bc.due_at AS due_at, (bc.due_at < :now) AS overdue
                FROM booking_commissions bc
                JOIN bookings b ON b.id = bc.booking_id
                JOIN venues v ON v.id = b.venue_id
                WHERE v.owner_id = :ownerId
            ) x
            GROUP BY x.venue_id, x.venue_name, x.status, x.overdue
            """, nativeQuery = true)
    java.util.List<DebtRow> debtRows(
            @org.springframework.data.repository.query.Param("ownerId") UUID ownerId,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);

    interface DebtRow {
        UUID getVenueId();

        String getVenueName();

        String getStatus();

        Boolean getOverdue();

        BigDecimal getTotal();

        long getBookings();

        java.time.Instant getEarliestDue();
    }

    @Query(value = """
            select coalesce(sum(commission_amount), 0) from booking_commissions
            where owner_id = :ownerId and status in ('pending', 'due', 'adjusted')
            """, nativeQuery = true)
    BigDecimal sumOutstandingByOwnerId(UUID ownerId);
}
