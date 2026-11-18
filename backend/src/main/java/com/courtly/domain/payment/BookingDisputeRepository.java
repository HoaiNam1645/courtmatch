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

    /** Bao cao dang xu ly (chua dong) cua mot don; toi da mot nho unique index bd_one_active_per_booking. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT d FROM BookingDispute d
            WHERE d.booking.id = :bookingId AND d.status IN ('open', 'awaiting_owner_response', 'reviewing')
            """)
    java.util.Optional<BookingDispute> findActiveByBooking(java.util.UUID bookingId);
}
