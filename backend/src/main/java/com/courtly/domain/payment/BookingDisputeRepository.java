package com.courtly.domain.payment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingDisputeRepository extends JpaRepository<BookingDispute, UUID> {

    /** Khoa dong de tai/xoa anh khong vuot gioi han khi nhieu yeu cau cung luc. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT d FROM BookingDispute d JOIN FETCH d.booking b JOIN FETCH b.venue v JOIN FETCH v.owner WHERE d.id = :id")
    java.util.Optional<BookingDispute> findByIdForUpdate(java.util.UUID id);

    @org.springframework.data.jpa.repository.Query("SELECT d FROM BookingDispute d JOIN FETCH d.booking b JOIN FETCH b.venue v JOIN FETCH v.owner WHERE d.id = :id")
    java.util.Optional<BookingDispute> findWithParties(java.util.UUID id);

    /** Bao cao cua mot nguoi (2.3.89), moi nhat truoc; {@code statuses} la tap trang thai can lay. */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT d FROM BookingDispute d JOIN FETCH d.booking b JOIN FETCH b.venue
            WHERE d.reporter.id = :userId AND d.status IN :statuses
            ORDER BY d.createdAt DESC, d.id
            """,
            countQuery = "SELECT count(d) FROM BookingDispute d WHERE d.reporter.id = :userId AND d.status IN :statuses")
    org.springframework.data.domain.Page<BookingDispute> findForReporter(java.util.UUID userId,
            java.util.Collection<String> statuses, org.springframework.data.domain.Pageable pageable);

    /** Bao cao ve don thuoc cac san cua mot chu san (2.3.93); cho giai trinh len dau theo han gan nhat. */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT d FROM BookingDispute d JOIN FETCH d.booking b JOIN FETCH b.venue v JOIN FETCH d.reporter
            WHERE v.owner.id = :ownerId AND (:venueId IS NULL OR v.id = :venueId) AND d.status IN :statuses
            ORDER BY CASE WHEN d.status = 'awaiting_owner_response' THEN 0 ELSE 1 END,
                     CASE WHEN d.status = 'awaiting_owner_response' THEN d.ownerResponseDeadline END ASC,
                     d.createdAt DESC, d.id
            """,
            countQuery = """
            SELECT count(d) FROM BookingDispute d JOIN d.booking b JOIN b.venue v
            WHERE v.owner.id = :ownerId AND (:venueId IS NULL OR v.id = :venueId) AND d.status IN :statuses
            """)
    org.springframework.data.domain.Page<BookingDispute> findForOwner(java.util.UUID ownerId, java.util.UUID venueId,
            java.util.Collection<String> statuses, org.springframework.data.domain.Pageable pageable);

    /** So bao cao dang cho chu san giai trinh. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT count(d) FROM BookingDispute d JOIN d.booking b JOIN b.venue v
            WHERE v.owner.id = :ownerId AND d.status = 'awaiting_owner_response'
            """)
    long countAwaitingForOwner(java.util.UUID ownerId);

    /** So bao cao cho giai trinh co han trong khoang ({@code from}, {@code to}]: sap het han. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT count(d) FROM BookingDispute d JOIN d.booking b JOIN b.venue v
            WHERE v.owner.id = :ownerId AND d.status = 'awaiting_owner_response'
              AND d.ownerResponseDeadline > :from AND d.ownerResponseDeadline <= :to
            """)
    long countAwaitingBetween(java.util.UUID ownerId, java.time.Instant from, java.time.Instant to);

    /** So bao cao cho giai trinh da qua han giai trinh cua chu san (chua duoc he thong tu xu ly). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT count(d) FROM BookingDispute d JOIN d.booking b JOIN b.venue v
            WHERE v.owner.id = :ownerId AND d.status = 'awaiting_owner_response' AND d.ownerResponseDeadline <= :now
            """)
    long countOverdueForOwner(java.util.UUID ownerId, java.time.Instant now);

    /** Bao cao chu san da duoc phep xem: chi bao cao ve don thuoc san cua minh. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT d FROM BookingDispute d JOIN FETCH d.booking b JOIN FETCH b.venue v JOIN FETCH d.reporter
            WHERE d.id = :id AND v.owner.id = :ownerId
            """)
    java.util.Optional<BookingDispute> findForOwnerById(java.util.UUID id, java.util.UUID ownerId);

    /** Bao cao dang xu ly (chua dong) cua mot don; toi da mot nho unique index bd_one_active_per_booking. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT d FROM BookingDispute d
            WHERE d.booking.id = :bookingId AND d.status IN ('open', 'awaiting_owner_response', 'reviewing')
            """)
    java.util.Optional<BookingDispute> findActiveByBooking(java.util.UUID bookingId);
}
