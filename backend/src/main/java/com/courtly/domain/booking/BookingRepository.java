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

    @Query(value = """
            SELECT b FROM Booking b
            JOIN FETCH b.venue v
            WHERE v.owner.id = :ownerId
              AND b.status IN :statuses
            ORDER BY b.createdAt DESC
            """,
            countQuery = """
                    SELECT count(b) FROM Booking b
                    WHERE b.venue.owner.id = :ownerId AND b.status IN :statuses
                    """)
    Page<Booking> findForOwner(@Param("ownerId") UUID ownerId,
                               @Param("statuses") Collection<BookingStatus> statuses,
                               Pageable pageable);

    /**
     * Yeu cau dat san cua chu san, loc theo san, trang thai va ngay choi (2.3.12).
     *
     * <p>Yeu cau sap het han hien truoc (han phan hoi tang dan); don da xu ly khong co han nen
     * xep theo thoi diem tao, moi nhat truoc. Tham so loc luon co gia tri (danh sach san,
     * khoang thoi gian rong) de tranh tham so NULL kho suy kieu trong PostgreSQL.
     */
    @Query(value = """
            SELECT b FROM Booking b
            JOIN FETCH b.venue v
            JOIN FETCH b.user
            WHERE v.id IN :venueIds
              AND b.status IN :statuses
              AND EXISTS (SELECT i.id FROM BookingItem i
                          WHERE i.booking = b AND i.startTime >= :from AND i.startTime < :to)
            ORDER BY b.ownerResponseExpiresAt ASC NULLS LAST, b.createdAt DESC
            """,
            countQuery = """
                    SELECT count(b) FROM Booking b
                    WHERE b.venue.id IN :venueIds
                      AND b.status IN :statuses
                      AND EXISTS (SELECT i.id FROM BookingItem i
                                  WHERE i.booking = b AND i.startTime >= :from AND i.startTime < :to)
                    """)
    Page<Booking> findRequestsForOwner(@Param("venueIds") Collection<UUID> venueIds,
                                       @Param("statuses") Collection<BookingStatus> statuses,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to,
                                       Pageable pageable);

    /**
     * Lich su dat san cua chu san (2.3.15): loc theo san, nhom trang thai, ngay choi va tu khoa
     * (ma don hoac ten nguoi dat, khong phan biet dau). Moi tham so luon co gia tri de tranh
     * tham so NULL kho suy kieu: tu khoa "%" = khong loc.
     */
    @Query(value = """
            SELECT b FROM Booking b
            JOIN FETCH b.venue v
            JOIN FETCH b.user u
            WHERE v.id IN :venueIds
              AND b.status IN :statuses
              AND (lower(b.bookingCode) LIKE lower(:keyword)
                   OR lower(function('courtly_unaccent', u.fullName)) LIKE lower(function('courtly_unaccent', :keyword)))
              AND EXISTS (SELECT i.id FROM BookingItem i
                          WHERE i.booking = b AND i.startTime >= :from AND i.startTime < :to)
            ORDER BY b.createdAt DESC
            """,
            countQuery = """
                    SELECT count(b) FROM Booking b
                    WHERE b.venue.id IN :venueIds
                      AND b.status IN :statuses
                      AND (lower(b.bookingCode) LIKE lower(:keyword)
                           OR lower(function('courtly_unaccent', b.user.fullName)) LIKE lower(function('courtly_unaccent', :keyword)))
                      AND EXISTS (SELECT i.id FROM BookingItem i
                                  WHERE i.booking = b AND i.startTime >= :from AND i.startTime < :to)
                    """)
    Page<Booking> searchHistoryForOwner(@Param("venueIds") Collection<UUID> venueIds,
                                        @Param("statuses") Collection<BookingStatus> statuses,
                                        @Param("keyword") String keyword,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to,
                                        Pageable pageable);

    /** So yeu cau dang cho chu san duyet va so yeu cau sap het han (2.3.12). */
    @Query("""
            SELECT count(b) FROM Booking b
            WHERE b.venue.id IN :venueIds
              AND b.status = com.courtly.common.enums.BookingStatus.PENDING_OWNER_CONFIRMATION
              AND b.ownerResponseExpiresAt > :now
              AND b.ownerResponseExpiresAt <= :soon
            """)
    long countExpiringSoon(@Param("venueIds") Collection<UUID> venueIds,
                           @Param("now") Instant now,
                           @Param("soon") Instant soon);

    @Query("""
            SELECT count(b) FROM Booking b
            WHERE b.venue.id IN :venueIds
              AND b.status = com.courtly.common.enums.BookingStatus.PENDING_OWNER_CONFIRMATION
            """)
    long countAwaitingOwner(@Param("venueIds") Collection<UUID> venueIds);

    /** Owner-review or direct-payment requests that passed their deadline. */
    @Query("""
            SELECT b FROM Booking b
            WHERE (b.status = com.courtly.common.enums.BookingStatus.PENDING_OWNER_CONFIRMATION
                   AND b.ownerResponseExpiresAt IS NOT NULL AND b.ownerResponseExpiresAt < :now)
               OR (b.status = com.courtly.common.enums.BookingStatus.AWAITING_PAYMENT
                   AND b.paymentExpiresAt IS NOT NULL AND b.paymentExpiresAt < :now
                   AND NOT EXISTS (
                       SELECT p.id FROM Payment p
                       WHERE p.booking = b
                         AND p.status = com.courtly.common.enums.PaymentStatus.PAYER_REPORTED))
            """)
    List<Booking> findExpiredPending(@Param("now") Instant now);

    @Query("""
            SELECT b FROM Booking b
            WHERE b.status = com.courtly.common.enums.BookingStatus.CONFIRMED
              AND NOT EXISTS (
                  SELECT i.id FROM BookingItem i
                  WHERE i.booking = b AND i.endTime > :now
              )
            """)
    List<Booking> findConfirmedFinished(@Param("now") Instant now);

    /**
     * Booking da choi xong, nguoi choi da bao thanh toan nhung chu san chua xac nhan thu tien.
     * Van phai hoan thanh de ghi nhan hoa hong, tranh viec khong bam xac nhan de ne phi.
     */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.status = com.courtly.common.enums.BookingStatus.AWAITING_PAYMENT
              AND EXISTS (
                  SELECT p.id FROM Payment p
                  WHERE p.booking = b
                    AND p.status = com.courtly.common.enums.PaymentStatus.PAYER_REPORTED)
              AND NOT EXISTS (
                  SELECT i.id FROM BookingItem i
                  WHERE i.booking = b AND i.endTime > :now
              )
            """)
    List<Booking> findUnconfirmedFinished(@Param("now") Instant now);

    /** Dem don tao trong ngay, dung de sinh so thu tu trong ma don. */
    @Query("""
            SELECT count(b) FROM Booking b
            WHERE b.createdAt >= :dayStart AND b.createdAt < :dayEnd
            """)
    long countCreatedBetween(@Param("dayStart") Instant dayStart, @Param("dayEnd") Instant dayEnd);

    boolean existsByBookingCode(String bookingCode);

    /** Booking da choi xong nhung nguoi dat chua ghi nhan tran dau (2.2.1). */
    @Query(value = """
            SELECT b FROM Booking b
            JOIN FETCH b.venue
            WHERE b.user.id = :userId
              AND b.status = com.courtly.common.enums.BookingStatus.COMPLETED
              AND b.completedAt >= :since
              AND NOT EXISTS (
                  SELECT m.id FROM Match m
                  WHERE m.booking = b
                    AND m.status <> com.courtly.common.enums.MatchStatus.CANCELLED
              )
            ORDER BY b.completedAt DESC
            """,
            countQuery = """
                    SELECT count(b) FROM Booking b
                    WHERE b.user.id = :userId
                      AND b.status = com.courtly.common.enums.BookingStatus.COMPLETED
                      AND b.completedAt >= :since
                      AND NOT EXISTS (
                          SELECT m.id FROM Match m
                          WHERE m.booking = b
                            AND m.status <> com.courtly.common.enums.MatchStatus.CANCELLED
                      )
                    """)
    Page<Booking> findMatchSuggestions(@Param("userId") UUID userId,
                                       @Param("since") Instant since,
                                       Pageable pageable);

    /** Booking dang xu ly cua mot san: cho chu san duyet, cho thanh toan hoac da xac nhan chua choi (2.3.35). */
    @Query(value = """
            select count(*) from bookings
            where venue_id = :venueId and status in ('pending_owner_confirmation', 'awaiting_payment', 'confirmed')
            """, nativeQuery = true)
    long countOpenByVenueId(@org.springframework.data.repository.query.Param("venueId") UUID venueId);
}
