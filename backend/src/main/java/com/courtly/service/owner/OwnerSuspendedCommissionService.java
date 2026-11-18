package com.courtly.service.owner;

import com.courtly.api.owner.dto.SuspendedCommissionResponse;
import com.courtly.api.owner.dto.SuspendedCommissionResponse.Item;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.97 - chu san xem hoa hong dang tam hoan do tranh chap. Chi doc; viec chuyen hoa hong sang suspended la 2.3.105
 * nen truoc do danh sach luon rong.
 */
@Service
@RequiredArgsConstructor
public class OwnerSuspendedCommissionService {

    private static final int LIMIT = 200;

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public SuspendedCommissionResponse list(UUID ownerId) {
        List<Item> items = jdbc.query("""
                SELECT b.id AS booking_id, b.booking_code, v.id AS venue_id, v.name AS venue_name, bc.commission_amount,
                       b.discount_amount, d.id AS dispute_id, bc.updated_at
                FROM booking_commissions bc
                JOIN bookings b ON b.id = bc.booking_id
                JOIN venues v ON v.id = b.venue_id
                LEFT JOIN LATERAL (
                    SELECT id FROM booking_disputes
                    WHERE booking_id = b.id AND status IN ('open', 'awaiting_owner_response', 'reviewing')
                    ORDER BY created_at DESC LIMIT 1) d ON true
                WHERE bc.owner_id = ? AND bc.status = 'suspended'
                ORDER BY bc.updated_at DESC
                LIMIT ?
                """, (rs, row) -> new Item(rs.getObject("booking_id", UUID.class), rs.getString("booking_code"),
                rs.getObject("venue_id", UUID.class), rs.getString("venue_name"), rs.getBigDecimal("commission_amount"),
                rs.getBigDecimal("discount_amount"), rs.getObject("dispute_id", UUID.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant()), ownerId, LIMIT);
        BigDecimal total = jdbc.queryForObject(
                "SELECT COALESCE(sum(commission_amount), 0) FROM booking_commissions WHERE owner_id = ? AND status = 'suspended'",
                BigDecimal.class, ownerId);
        return new SuspendedCommissionResponse(items, total);
    }
}
