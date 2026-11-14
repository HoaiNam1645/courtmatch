package com.courtly.domain.account;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Tong quan chu san cho quan tri vien (2.3.32); chi doc. */
public interface AdminOwnerQueries extends Repository<User, UUID> {

    /** Tai khoan giu vai tro chu san, cu nhat truoc; he thong chi co mot chu san. */
    @Query("""
            select u from User u
            where exists (select 1 from UserRole ur where ur.user = u and ur.role.code = 'owner')
            order by u.createdAt asc, u.id asc
            """)
    List<User> findOwners();

    @Query(value = "select approval_status as status, count(*) as total from venues where owner_id = :ownerId group by approval_status", nativeQuery = true)
    List<StatusCount> venuesByApproval(@Param("ownerId") UUID ownerId);

    /** Booking dang xu ly cua cac san cua chu san: cho chu san duyet, cho thanh toan, da xac nhan chua choi. */
    @Query(value = """
            select count(*) from bookings b join venues v on v.id = b.venue_id
            where v.owner_id = :ownerId and b.status in ('pending_owner_confirmation', 'awaiting_payment', 'confirmed')
            """, nativeQuery = true)
    long activeBookings(@Param("ownerId") UUID ownerId);

    interface StatusCount {
        String getStatus();

        long getTotal();
    }
}
