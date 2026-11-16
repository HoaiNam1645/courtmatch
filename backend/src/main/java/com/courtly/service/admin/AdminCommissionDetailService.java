package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminCommissionDetailResponse;
import com.courtly.api.admin.dto.AdminCommissionDetailResponse.BookingInfo;
import com.courtly.api.admin.dto.AdminCommissionDetailResponse.Commission;
import com.courtly.api.admin.dto.AdminCommissionDetailResponse.Dispute;
import com.courtly.api.admin.dto.AdminCommissionDetailResponse.HistoryEntry;
import com.courtly.api.admin.dto.AdminCommissionDetailResponse.Owner;
import com.courtly.api.admin.dto.AdminCommissionDetailResponse.Payment;
import com.courtly.api.admin.dto.AdminCommissionDetailResponse.Refund;
import com.courtly.api.admin.dto.AdminCommissionDetailResponse.Settlement;
import com.courtly.common.exception.ApiException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.44 - quan tri vien xem chi tiet doi soat cua mot booking.
 *
 * <p>Chi doc; hoa hong la ban chot luc booking hoan thanh nen khong tinh lai theo chinh sach hien hanh.
 */
@Service
@RequiredArgsConstructor
public class AdminCommissionDetailService {

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AdminCommissionDetailResponse get(UUID adminId, UUID commissionId) {
        accessService.requirePermission(adminId, AdminCommissionTransactionService.PERMISSION);
        return load(commissionId);
    }

    /** Doc chi tiet khong kiem tra quyen: nguoi goi da kiem tra (vi du sau khi dieu chinh hoa hong, 2.3.51). */
    AdminCommissionDetailResponse load(UUID commissionId) {

        List<Row> found = jdbc.query("""
                SELECT bc.id, bc.status, bc.fee_type, bc.fee_value, pfc.name AS policy_name, bc.gross_amount, bc.commission_amount,
                       bc.voucher_discount, bc.courtly_owed, bc.courtly_owed_cleared_at, bc.calculated_at, bc.due_at, bc.settled_at,
                       (bc.status IN ('pending', 'due', 'adjusted') AND bc.due_at < now()) AS overdue,
                       b.id AS booking_id, b.booking_code, b.status AS booking_status, b.total_amount, b.discount_amount, b.completed_at,
                       v.id AS venue_id, v.name AS venue_name, pl.full_name AS player_name, vc.code AS voucher_code,
                       ow.id AS owner_id, ow.full_name AS owner_name, ow.email AS owner_email, ow.phone AS owner_phone,
                       (SELECT min(i.start_time) FROM booking_items i WHERE i.booking_id = b.id) AS start_time,
                       (SELECT max(i.end_time) FROM booking_items i WHERE i.booking_id = b.id) AS end_time,
                       cs.id AS settlement_id, cs.period_from, cs.period_to, cs.status AS settlement_status,
                       cs.amount_due, cs.amount_paid
                FROM booking_commissions bc
                JOIN bookings b ON b.id = bc.booking_id
                JOIN venues v ON v.id = b.venue_id
                JOIN users pl ON pl.id = b.user_id
                JOIN users ow ON ow.id = bc.owner_id
                LEFT JOIN platform_fee_configs pfc ON pfc.id = bc.fee_config_id
                LEFT JOIN vouchers vc ON vc.id = b.voucher_id
                LEFT JOIN commission_settlements cs ON cs.id = bc.settlement_id
                WHERE bc.id = ?
                """, (rs, index) -> new Row(rs), commissionId);
        if (found.isEmpty()) {
            throw ApiException.notFound("Khong tim thay giao dich doi soat.");
        }
        Row r = found.getFirst();
        UUID bookingId = r.rs.bookingId;

        BigDecimal base = r.rs.commissionAmount.add(r.rs.voucherDiscount).subtract(r.rs.courtlyOwed);
        Commission commission = new Commission(commissionId, r.rs.status, r.rs.feeType, r.rs.feeValue, r.rs.policyName,
                r.rs.grossAmount, base, r.rs.voucherDiscount, r.rs.commissionAmount, r.rs.courtlyOwed, r.rs.clearedAt,
                r.rs.calculatedAt, r.rs.dueAt, r.rs.overdue, r.rs.settledAt);
        BookingInfo booking = new BookingInfo(bookingId, r.rs.bookingCode, r.rs.bookingStatus, r.rs.venueId, r.rs.venueName,
                r.rs.playerName, r.rs.totalAmount, r.rs.discountAmount, r.rs.totalAmount.subtract(r.rs.discountAmount),
                r.rs.voucherCode, r.rs.startTime, r.rs.endTime, r.rs.completedAt);
        Owner owner = new Owner(r.rs.ownerId, r.rs.ownerName, r.rs.ownerEmail, r.rs.ownerPhone);
        Settlement settlement = r.rs.settlementId == null ? null : new Settlement(r.rs.settlementId, r.rs.periodFrom,
                r.rs.periodTo, r.rs.settlementStatus, r.rs.amountDue, r.rs.amountPaid);

        Payment payment = jdbc.query("""
                SELECT id, status, method, amount, payer_reported_at, confirmed_at, rejection_reason
                FROM payments WHERE booking_id = ? ORDER BY created_at DESC LIMIT 1
                """, (rs, index) -> new Payment(rs.getObject("id", UUID.class), rs.getString("status"), rs.getString("method"),
                rs.getBigDecimal("amount"), instant(rs, "payer_reported_at"), instant(rs, "confirmed_at"),
                rs.getString("rejection_reason")), bookingId).stream().findFirst().orElse(null);
        boolean unconfirmed = "completed".equals(r.rs.bookingStatus) && payment != null && !"confirmed".equals(payment.status())
                && !"partially_refunded".equals(payment.status()) && !"refunded".equals(payment.status());

        List<Refund> refunds = jdbc.query("""
                SELECT id, amount, status, reason, created_at FROM refunds WHERE booking_id = ? ORDER BY created_at DESC
                """, (rs, index) -> new Refund(rs.getObject("id", UUID.class), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getString("reason"), instant(rs, "created_at")), bookingId);
        List<Dispute> disputes = jdbc.query("""
                SELECT id, category, status, resolution, created_at FROM booking_disputes WHERE booking_id = ? ORDER BY created_at DESC
                """, (rs, index) -> new Dispute(rs.getObject("id", UUID.class), rs.getString("category"), rs.getString("status"),
                rs.getString("resolution"), instant(rs, "created_at")), bookingId);
        List<HistoryEntry> history = jdbc.query("""
                SELECT a.id, a.action, u.full_name AS actor_name, a.old_data::text AS old_data, a.new_data::text AS new_data, a.created_at
                FROM audit_logs a LEFT JOIN users u ON u.id = a.actor_id
                WHERE a.entity_type = 'booking_commission' AND a.entity_id = ?
                ORDER BY a.created_at DESC
                """, (rs, index) -> new HistoryEntry(rs.getObject("id", UUID.class), rs.getString("action"),
                rs.getString("actor_name"), json(rs.getString("old_data")), json(rs.getString("new_data")),
                instant(rs, "created_at")), commissionId);

        return new AdminCommissionDetailResponse(commission, booking, owner, payment, unconfirmed, settlement, refunds, disputes, history);
    }

    private Object json(String text) {
        if (text == null) {
            return null;
        }
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return text;
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    /** Dong du lieu goc cua truy van chinh, tach ra de doc cot an toan. */
    private static final class Row {
        private final Data rs;

        Row(ResultSet set) throws SQLException {
            this.rs = new Data(set);
        }
    }

    private static final class Data {
        final String status;
        final String feeType;
        final BigDecimal feeValue;
        final String policyName;
        final BigDecimal grossAmount;
        final BigDecimal commissionAmount;
        final BigDecimal voucherDiscount;
        final BigDecimal courtlyOwed;
        final Instant clearedAt;
        final Instant calculatedAt;
        final Instant dueAt;
        final Instant settledAt;
        final boolean overdue;
        final UUID bookingId;
        final String bookingCode;
        final String bookingStatus;
        final BigDecimal totalAmount;
        final BigDecimal discountAmount;
        final Instant completedAt;
        final UUID venueId;
        final String venueName;
        final String playerName;
        final String voucherCode;
        final UUID ownerId;
        final String ownerName;
        final String ownerEmail;
        final String ownerPhone;
        final Instant startTime;
        final Instant endTime;
        final UUID settlementId;
        final java.time.LocalDate periodFrom;
        final java.time.LocalDate periodTo;
        final String settlementStatus;
        final BigDecimal amountDue;
        final BigDecimal amountPaid;

        Data(ResultSet rs) throws SQLException {
            status = rs.getString("status");
            feeType = rs.getString("fee_type");
            feeValue = rs.getBigDecimal("fee_value");
            policyName = rs.getString("policy_name");
            grossAmount = rs.getBigDecimal("gross_amount");
            commissionAmount = rs.getBigDecimal("commission_amount");
            voucherDiscount = rs.getBigDecimal("voucher_discount");
            courtlyOwed = rs.getBigDecimal("courtly_owed");
            clearedAt = instant(rs, "courtly_owed_cleared_at");
            calculatedAt = instant(rs, "calculated_at");
            dueAt = instant(rs, "due_at");
            settledAt = instant(rs, "settled_at");
            overdue = rs.getBoolean("overdue");
            bookingId = rs.getObject("booking_id", UUID.class);
            bookingCode = rs.getString("booking_code");
            bookingStatus = rs.getString("booking_status");
            totalAmount = rs.getBigDecimal("total_amount");
            discountAmount = rs.getBigDecimal("discount_amount");
            completedAt = instant(rs, "completed_at");
            venueId = rs.getObject("venue_id", UUID.class);
            venueName = rs.getString("venue_name");
            playerName = rs.getString("player_name");
            voucherCode = rs.getString("voucher_code");
            ownerId = rs.getObject("owner_id", UUID.class);
            ownerName = rs.getString("owner_name");
            ownerEmail = rs.getString("owner_email");
            ownerPhone = rs.getString("owner_phone");
            startTime = instant(rs, "start_time");
            endTime = instant(rs, "end_time");
            settlementId = rs.getObject("settlement_id", UUID.class);
            periodFrom = rs.getDate("period_from") == null ? null : rs.getDate("period_from").toLocalDate();
            periodTo = rs.getDate("period_to") == null ? null : rs.getDate("period_to").toLocalDate();
            settlementStatus = rs.getString("settlement_status");
            amountDue = rs.getBigDecimal("amount_due");
            amountPaid = rs.getBigDecimal("amount_paid");
        }
    }
}
