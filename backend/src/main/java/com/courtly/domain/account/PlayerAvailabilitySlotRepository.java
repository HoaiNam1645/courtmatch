package com.courtly.domain.account;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerAvailabilitySlotRepository extends JpaRepository<PlayerAvailabilitySlot, UUID> {

    List<PlayerAvailabilitySlot> findAllByUserIdOrderByDayOfWeekAscStartTimeAsc(UUID userId);

    @Query("""
            select slot from PlayerAvailabilitySlot slot
            where slot.user.id in :userIds
            order by slot.user.id, slot.dayOfWeek, slot.startTime
            """)
    List<PlayerAvailabilitySlot> findAllByUserIds(@Param("userIds") Collection<UUID> userIds);

    void deleteAllByUserId(UUID userId);
}
