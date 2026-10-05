package com.courtly.domain.account;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerAvailabilitySlotRepository extends JpaRepository<PlayerAvailabilitySlot, UUID> {

    List<PlayerAvailabilitySlot> findAllByUserIdOrderByDayOfWeekAscStartTimeAsc(UUID userId);

    void deleteAllByUserId(UUID userId);
}
