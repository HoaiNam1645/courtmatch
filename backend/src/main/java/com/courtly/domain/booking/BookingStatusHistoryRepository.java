package com.courtly.domain.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface BookingStatusHistoryRepository extends JpaRepository<BookingStatusHistory, UUID> {
}
