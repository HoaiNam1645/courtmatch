package com.courtly.domain.payment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingDisputeRepository extends JpaRepository<BookingDispute, UUID> {

    /** Bao cao dang xu ly (chua dong) cua mot don; toi da mot nho unique index bd_one_active_per_booking. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT d FROM BookingDispute d
            WHERE d.booking.id = :bookingId AND d.status IN ('open', 'awaiting_owner_response', 'reviewing')
            """)
    java.util.Optional<BookingDispute> findActiveByBooking(java.util.UUID bookingId);
}
