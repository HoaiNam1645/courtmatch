package com.courtly.service.dispute;

import com.courtly.service.audit.AuditService;
import com.courtly.service.notification.DisputeNotifier;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 2.3.105 / 2.3.110: tam hoan hoa hong cua don dang tranh chap va khoi phuc khi bao cao ket thuc ma khong co khoan hoan.
 *
 * <p>Courtly khong giu tien: tam hoan chi la loai hoa hong cua dung don do khoi ky doi soat va khoi cong no den han (moi noi tinh
 * cong no chi cong pending/due/adjusted). Hoa hong da gan ky doi soat hoac da dong khong tam hoan duoc. Goi ben trong transaction cua
 * nghiep vu bao cao.
 */
@Service
@RequiredArgsConstructor
public class DisputeCommissionService {

    private static final List<String> HOLDABLE = List.of("pending", "due", "adjusted");

    private final JdbcTemplate jdbc;
    private final AuditService auditService;
    private final DisputeNotifier disputeNotifier;

    /** Tam hoan hoa hong cua don khi co bao cao. Tra ve true neu da tam hoan. */
    public boolean hold(UUID bookingId, UUID disputeId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, status, settlement_id FROM booking_commissions WHERE booking_id = ? FOR UPDATE", bookingId);
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> row = rows.getFirst();
        String status = (String) row.get("status");
        if (!HOLDABLE.contains(status) || row.get("settlement_id") != null) {
            return false;
        }
        jdbc.update("UPDATE booking_commissions SET status = 'suspended', status_before = ?, suspended_at = now(), suspended_dispute_id = ? WHERE id = ?",
                status, disputeId, row.get("id"));
        auditService.record(null, "commission.suspend", "booking_commission", (UUID) row.get("id"),
                Map.of("status", status), Map.of("status", "suspended", "disputeId", disputeId.toString(), "bookingId", bookingId.toString()));
        return true;
    }

    /**
     * Khoi phuc hoa hong da tam hoan ve trang thai truoc do. Han thanh toan khong lui: neu da qua han trong luc tam hoan thi den han
     * ngay luc khoi phuc (khong bi tinh cham cho thoi gian tam hoan). Chi khoi phuc mot lan.
     */
    public boolean restore(UUID bookingId, UUID disputeId, String reason) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, owner_id, status_before, due_at, commission_amount FROM booking_commissions WHERE booking_id = ? AND status = 'suspended' FOR UPDATE",
                bookingId);
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> row = rows.getFirst();
        String before = row.get("status_before") == null ? "due" : (String) row.get("status_before");
        Instant due = ((java.sql.Timestamp) row.get("due_at")).toInstant();
        Instant now = Instant.now();
        Instant newDue = due.isBefore(now) ? now : due;
        jdbc.update("UPDATE booking_commissions SET status = ?, due_at = ?, status_before = NULL, suspended_at = NULL, suspended_dispute_id = NULL WHERE id = ?",
                before, Timestamp.from(newDue), row.get("id"));
        auditService.record(null, "commission.restore", "booking_commission", (UUID) row.get("id"),
                Map.of("status", "suspended"), Map.of("status", before, "dueAt", newDue.toString(), "disputeId", disputeId.toString(), "reason", reason));
        disputeNotifier.commissionRestored((UUID) row.get("owner_id"), disputeId, bookingId, (BigDecimal) row.get("commission_amount"), newDue);
        return true;
    }
}
