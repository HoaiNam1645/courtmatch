package com.courtly.domain.finance;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingCommissionRepository extends JpaRepository<BookingCommission, UUID> {

    Optional<BookingCommission> findByBookingId(UUID bookingId);

    @Query(value = """
            select coalesce(sum(commission_amount), 0) from booking_commissions
            where owner_id = :ownerId and status in ('pending', 'due', 'adjusted')
            """, nativeQuery = true)
    BigDecimal sumOutstandingByOwnerId(UUID ownerId);
}
