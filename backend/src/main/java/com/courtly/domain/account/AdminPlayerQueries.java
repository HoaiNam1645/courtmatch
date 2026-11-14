package com.courtly.domain.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Thong ke ve mot nguoi choi cho quan tri vien (2.3.31); chi doc. */
public interface AdminPlayerQueries extends Repository<User, UUID> {

    @Query(value = "select user_id as userId, count(*) as total from bookings where user_id in (:userIds) group by user_id", nativeQuery = true)
    List<UserCount> bookingCounts(@Param("userIds") Collection<UUID> userIds);

    @Query(value = "select status as status, count(*) as total from bookings where user_id = :userId group by status", nativeQuery = true)
    List<StatusCount> bookingCountsByStatus(@Param("userId") UUID userId);

    @Query(value = """
            select b.id as id, b.booking_code as bookingCode, v.name as venueName, b.status as status,
                   b.total_amount as totalAmount, b.created_at as createdAt
            from bookings b join venues v on v.id = b.venue_id
            where b.user_id = :userId order by b.created_at desc limit 10
            """, nativeQuery = true)
    List<RecentBooking> recentBookings(@Param("userId") UUID userId);

    @Query(value = "select count(*) from match_players where user_id = :userId", nativeQuery = true)
    long matchCount(@Param("userId") UUID userId);

    @Query(value = "select count(*) from reports where target_type = 'user' and target_id = :userId", nativeQuery = true)
    long reportCount(@Param("userId") UUID userId);

    interface UserCount {
        UUID getUserId();

        long getTotal();
    }

    interface StatusCount {
        String getStatus();

        long getTotal();
    }

    interface RecentBooking {
        UUID getId();

        String getBookingCode();

        String getVenueName();

        String getStatus();

        BigDecimal getTotalAmount();

        Instant getCreatedAt();
    }
}
