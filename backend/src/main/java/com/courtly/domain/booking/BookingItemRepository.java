package com.courtly.domain.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingItemRepository extends JpaRepository<BookingItem, UUID> {

    /**
     * Cac khung gio da bi giu tren nhieu san con, dung de dung lich trong (2.1.26).
     *
     * <p>Chi tinh item pending/confirmed - dung tap trang thai ma exclusion constraint
     * {@code booking_items_no_overlap} bao ve, nen ket qua khop voi cai database cho phep.
     */
    @Query("""
            SELECT i FROM BookingItem i
            JOIN FETCH i.court
            WHERE i.court.id IN :courtIds
              AND i.status IN (com.courtly.common.enums.BookingItemStatus.PENDING,
                               com.courtly.common.enums.BookingItemStatus.CONFIRMED)
              AND i.startTime < :windowEnd
              AND i.endTime > :windowStart
            """)
    List<BookingItem> findOccupiedForCourts(@Param("courtIds") Collection<UUID> courtIds,
                                            @Param("windowStart") Instant windowStart,
                                            @Param("windowEnd") Instant windowEnd);

    /** Kiem tra mot khung gio cu the con trong khong, truoc khi bao gia (2.1.27). */
    @Query("""
            SELECT count(i) > 0 FROM BookingItem i
            WHERE i.court.id = :courtId
              AND i.status IN (com.courtly.common.enums.BookingItemStatus.PENDING,
                               com.courtly.common.enums.BookingItemStatus.CONFIRMED)
              AND i.startTime < :endTime
              AND i.endTime > :startTime
            """)
    boolean existsOverlapping(@Param("courtId") UUID courtId,
                              @Param("startTime") Instant startTime,
                              @Param("endTime") Instant endTime);
}
