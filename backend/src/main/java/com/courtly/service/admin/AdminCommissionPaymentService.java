package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminPaymentResponses.ListResponse;
import com.courtly.api.admin.dto.AdminPaymentResponses.OwnerOption;
import com.courtly.api.admin.dto.AdminPaymentResponses.Row;
import com.courtly.api.admin.dto.AdminPaymentResponses.Settlement;
import com.courtly.api.admin.dto.AdminPaymentResponses.Totals;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.service.audit.AuditService;
import com.courtly.service.owner.OwnerRestrictionService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.47 - 2.3.50: quan tri vien xu ly thanh toan cong no hoa hong cua chu san.
 *
 * <p>Courtly khong giu tien cua nguoi choi; day la khoan chu san tu chuyen hoa hong cho Courtly. Cong no chi giam khi
 * quan tri vien xac nhan. Xac nhan gan so tien vao ky doi soat (neu bao cao gan ky) hoac dong dan cac hoa hong cu nhat chua
 * thuoc ky nao, va bu tru khoan Courtly no chu san (courtly_owed) neu con cong no de bu tru.
 */
@Service
@RequiredArgsConstructor
public class AdminCommissionPaymentService {

    static final String VIEW = "finance.commission.view";
    static final String MANAGE = "finance.commission.manage";
    static final List<String> STATUSES = List.of("pending_review", "confirmed", "rejected");

    private static final String ROW_SELECT = """
            SELECT r.id, r.owner_id, u.full_name AS owner_name, u.email AS owner_email, r.settlement_id, cs.period_from, cs.period_to,
                   cs.status AS settlement_status, r.amount, r.courtly_offset_amount, r.paid_on, r.reference_code, r.proof_url, r.note,
                   r.status, r.review_note, rv.full_name AS reviewer, r.reviewed_at, r.created_at
            FROM commission_payment_reports r
            JOIN users u ON u.id = r.owner_id
            LEFT JOIN users rv ON rv.id = r.reviewed_by
            LEFT JOIN commission_settlements cs ON cs.id = r.settlement_id
            """;

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;
    private final OwnerRestrictionService restrictionService;

    /**
     * @param q            tim theo ma tham chieu hoac ten/email chu san
     * @param status       all hoac pending_review, confirmed, rejected
     * @param ownerId      null = moi chu san
     * @param settlementId null = moi ky
     * @param from         ngay thanh toan tu ngay nay
     * @param to           ngay thanh toan den het ngay nay
     */
    @Transactional(readOnly = true)
    public ListResponse list(UUID adminId, String q, String status, UUID ownerId, UUID settlementId, LocalDate from, LocalDate to,
                             int page, int size) {
        accessService.requirePermission(adminId, VIEW);
        if (status != null && !status.isBlank() && !"all".equals(status) && !STATUSES.contains(status)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai khong hop le.",
                    Map.of("status", "Chi nhan all hoac " + String.join(", ", STATUSES)));
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ngay ket thuc phai sau hoac bang ngay bat dau.",
                    Map.of("to", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        if (ownerId != null && !exists("users", ownerId)) {
            throw ApiException.notFound("Khong tim thay chu san.");
        }
        if (settlementId != null && !exists("commission_settlements", settlementId)) {
            throw ApiException.notFound("Khong tim thay ky doi soat.");
        }

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            where.append(" AND (lower(r.reference_code) LIKE ? ESCAPE '\\' OR lower(u.full_name) LIKE ? ESCAPE '\\' OR lower(u.email) LIKE ? ESCAPE '\\')");
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (ownerId != null) {
            where.append(" AND r.owner_id = ?");
            args.add(ownerId);
        }
        if (settlementId != null) {
            where.append(" AND r.settlement_id = ?");
            args.add(settlementId);
        }
        if (from != null) {
            where.append(" AND r.paid_on >= ?");
            args.add(java.sql.Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND r.paid_on <= ?");
            args.add(java.sql.Date.valueOf(to));
        }

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        STATUSES.forEach(value -> statusCounts.put(value, 0L));
        jdbc.query("SELECT r.status AS status, count(*) AS total FROM commission_payment_reports r JOIN users u ON u.id = r.owner_id"
                + where + " GROUP BY r.status", rs -> {
            statusCounts.put(rs.getString("status"), rs.getLong("total"));
        }, args.toArray());

        String filteredWhere = where.toString();
        List<Object> filteredArgs = new ArrayList<>(args);
        if (status != null && !status.isBlank() && !"all".equals(status)) {
            filteredWhere += " AND r.status = ?";
            filteredArgs.add(status);
        }
        Totals totals = jdbc.queryForObject(
                "SELECT count(*) AS total, coalesce(sum(r.amount), 0) AS amount FROM commission_payment_reports r JOIN users u ON u.id = r.owner_id"
                        + filteredWhere,
                (rs, row) -> new Totals(rs.getLong("total"), rs.getBigDecimal("amount")), filteredArgs.toArray());

        List<Object> pageArgs = new ArrayList<>(filteredArgs);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<Row> rows = jdbc.query(ROW_SELECT + filteredWhere
                + " ORDER BY (r.status = 'pending_review') DESC, r.created_at DESC, r.id LIMIT ? OFFSET ?",
                (rs, index) -> row(rs), pageArgs.toArray());

        int totalPages = (int) Math.ceil(totals.count() / (double) size);
        return new ListResponse(rows, page, size, totals.count(), totalPages, totals, statusCounts, ownerOptions(), null);
    }

    /** 2.3.47 - xac nhan chu san da thanh toan cong no. */
    @Transactional
    public Row confirm(UUID adminId, UUID reportId, String note) {
        User admin = accessService.requirePermission(adminId, MANAGE);
        Report report = lockPending(reportId);
        Instant now = Instant.now();
        jdbc.queryForObject("SELECT user_id FROM court_owner_profiles WHERE user_id = ? FOR UPDATE", UUID.class, report.ownerId);

        BigDecimal credit = jdbc.queryForObject("SELECT commission_credit FROM court_owner_profiles WHERE user_id = ?",
                BigDecimal.class, report.ownerId);
        BigDecimal remaining;
        BigDecimal paidBefore = BigDecimal.ZERO;
        BigDecimal due = BigDecimal.ZERO;
        String settlementStatus = null;
        if (report.settlementId != null) {
            Map<String, Object> settlement = jdbc.queryForMap(
                    "SELECT amount_due, amount_paid, status FROM commission_settlements WHERE id = ? FOR UPDATE", report.settlementId);
            due = (BigDecimal) settlement.get("amount_due");
            paidBefore = (BigDecimal) settlement.get("amount_paid");
            settlementStatus = (String) settlement.get("status");
            if ("paid".equals(settlementStatus)) {
                throw conflict("Ky doi soat nay da thanh toan xong.");
            }
            remaining = due.subtract(paidBefore);
        } else {
            BigDecimal debt = jdbc.queryForObject("""
                    SELECT coalesce(sum(commission_amount), 0) FROM booking_commissions
                    WHERE owner_id = ? AND settlement_id IS NULL AND status IN ('pending', 'due', 'adjusted')
                    """, BigDecimal.class, report.ownerId);
            remaining = debt.subtract(credit);
        }
        if (report.amount.compareTo(remaining) > 0) {
            throw conflict("So tien bao cao vuot qua so con no (" + remaining.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
                    + " dong). Hay tu choi bao cao hoac dieu chinh cong no truoc.");
        }

        // Bu tru khoan Courtly no chu san vao phan cong no con lai sau khi tru tien thanh toan.
        BigDecimal budget = remaining.subtract(report.amount);
        BigDecimal offset = BigDecimal.ZERO;
        List<Object[]> owed = jdbc.query("""
                SELECT id, courtly_owed FROM booking_commissions
                WHERE owner_id = ? AND courtly_owed > 0 AND courtly_owed_cleared_at IS NULL AND status NOT IN ('waived', 'suspended')
                ORDER BY calculated_at, id FOR UPDATE
                """, (rs, index) -> new Object[] {rs.getObject("id", UUID.class), rs.getBigDecimal("courtly_owed")}, report.ownerId);
        List<UUID> clearedOwed = new ArrayList<>();
        for (Object[] item : owed) {
            BigDecimal amount = (BigDecimal) item[1];
            if (amount.compareTo(budget) <= 0) {
                jdbc.update("UPDATE booking_commissions SET courtly_owed_cleared_at = ? WHERE id = ?", Timestamp.from(now), item[0]);
                budget = budget.subtract(amount);
                offset = offset.add(amount);
                clearedOwed.add((UUID) item[0]);
            }
        }
        BigDecimal pool = report.amount.add(offset);

        List<UUID> settled;
        String newSettlementStatus = null;
        BigDecimal paidAfter = null;
        BigDecimal creditAfter = credit;
        if (report.settlementId != null) {
            paidAfter = paidBefore.add(pool);
            boolean fullyPaid = paidAfter.compareTo(due) >= 0;
            settled = settleSettlementCommissions(report.settlementId, paidAfter, fullyPaid, now);
            newSettlementStatus = fullyPaid ? "paid" : "partial";
            jdbc.update("""
                    UPDATE commission_settlements SET amount_paid = ?, status = ?, paid_at = ?, reviewed_by = ?, reviewed_at = ? WHERE id = ?
                    """, paidAfter, newSettlementStatus, fullyPaid ? Timestamp.from(now) : null, admin.getId(), Timestamp.from(now),
                    report.settlementId);
        } else {
            BigDecimal[] left = {credit.add(pool)};
            settled = new ArrayList<>();
            List<Object[]> unsettled = jdbc.query("""
                    SELECT id, commission_amount FROM booking_commissions
                    WHERE owner_id = ? AND settlement_id IS NULL AND status IN ('pending', 'due', 'adjusted')
                    ORDER BY due_at, id FOR UPDATE
                    """, (rs, index) -> new Object[] {rs.getObject("id", UUID.class), rs.getBigDecimal("commission_amount")}, report.ownerId);
            for (Object[] item : unsettled) {
                BigDecimal amount = (BigDecimal) item[1];
                if (amount.compareTo(left[0]) > 0) {
                    break;
                }
                jdbc.update("UPDATE booking_commissions SET status = 'settled', settled_at = ? WHERE id = ?", Timestamp.from(now), item[0]);
                left[0] = left[0].subtract(amount);
                settled.add((UUID) item[0]);
            }
            creditAfter = left[0];
            jdbc.update("UPDATE court_owner_profiles SET commission_credit = ? WHERE user_id = ?", creditAfter, report.ownerId);
        }

        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        jdbc.update("""
                UPDATE commission_payment_reports SET status = 'confirmed', reviewed_by = ?, reviewed_at = ?, review_note = ?,
                       courtly_offset_amount = ? WHERE id = ?
                """, admin.getId(), Timestamp.from(now), cleanNote, offset, reportId);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("reportId", reportId.toString());
        audit.put("ownerId", report.ownerId.toString());
        audit.put("referenceCode", report.referenceCode);
        audit.put("amount", report.amount.toPlainString());
        audit.put("courtlyOffset", offset.toPlainString());
        audit.put("settledCommissions", settled.size());
        if (report.settlementId != null) {
            audit.put("settlementId", report.settlementId.toString());
            audit.put("settlementStatus", newSettlementStatus);
            audit.put("amountPaid", paidAfter.toPlainString());
        } else {
            audit.put("credit", creditAfter.toPlainString());
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", "pending_review");
        auditService.record(admin, "settlement.payment.confirm", "commission_payment_reports", reportId, before, audit);
        if (report.settlementId != null) {
            auditService.record(admin, "settlement.payment.confirm", "commission_settlement", report.settlementId,
                    Map.of("amountPaid", paidBefore.toPlainString(), "status", settlementStatus), audit);
        }
        for (UUID commissionId : settled) {
            auditService.record(admin, "commission.settle", "booking_commission", commissionId, Map.of("status", "due"),
                    Map.of("reportId", reportId.toString()));
        }
        for (UUID commissionId : clearedOwed) {
            auditService.record(admin, "commission.courtly_owed.clear", "booking_commission", commissionId, null,
                    Map.of("reportId", reportId.toString()));
        }

        restrictionService.recompute(report.ownerId);
        return jdbc.queryForObject(ROW_SELECT + " WHERE r.id = ?", (rs, index) -> row(rs), reportId);
    }

    /** Dong dan hoa hong cua ky theo han thanh toan: du tien thi dong, thanh toan du ky thi dong het. */
    private List<UUID> settleSettlementCommissions(UUID settlementId, BigDecimal paidAfter, boolean fullyPaid, Instant now) {
        BigDecimal alreadySettled = jdbc.queryForObject(
                "SELECT coalesce(sum(commission_amount), 0) FROM booking_commissions WHERE settlement_id = ? AND status = 'settled'",
                BigDecimal.class, settlementId);
        BigDecimal budget = paidAfter.subtract(alreadySettled);
        List<UUID> settled = new ArrayList<>();
        List<Object[]> unsettled = jdbc.query("""
                SELECT id, commission_amount FROM booking_commissions
                WHERE settlement_id = ? AND status IN ('pending', 'due', 'adjusted') ORDER BY due_at, id FOR UPDATE
                """, (rs, index) -> new Object[] {rs.getObject("id", UUID.class), rs.getBigDecimal("commission_amount")}, settlementId);
        for (Object[] item : unsettled) {
            BigDecimal amount = (BigDecimal) item[1];
            if (!fullyPaid && amount.compareTo(budget) > 0) {
                break;
            }
            jdbc.update("UPDATE booking_commissions SET status = 'settled', settled_at = ? WHERE id = ?", Timestamp.from(now), item[0]);
            budget = budget.subtract(amount);
            settled.add((UUID) item[0]);
        }
        return settled;
    }

    private Report lockPending(UUID reportId) {
        List<Report> found = jdbc.query("""
                SELECT id, owner_id, settlement_id, amount, reference_code, status FROM commission_payment_reports WHERE id = ? FOR UPDATE
                """, (rs, index) -> new Report(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
                rs.getObject("settlement_id", UUID.class), rs.getBigDecimal("amount"), rs.getString("reference_code"),
                rs.getString("status")), reportId);
        if (found.isEmpty()) {
            throw ApiException.notFound("Khong tim thay bao cao thanh toan.");
        }
        Report report = found.getFirst();
        if (!"pending_review".equals(report.status)) {
            throw conflict("Bao cao nay da duoc xu ly.");
        }
        return report;
    }

    private static ApiException conflict(String message) {
        return ApiException.conflict(ErrorCode.COMMISSION_PAYMENT_INVALID, message, Map.of("report", message));
    }

    private record Report(UUID id, UUID ownerId, UUID settlementId, BigDecimal amount, String referenceCode, String status) {
    }

    private Row row(ResultSet rs) throws SQLException {
        UUID settlementId = rs.getObject("settlement_id", UUID.class);
        Settlement settlement = settlementId == null ? null
                : new Settlement(settlementId, rs.getDate("period_from").toLocalDate(), rs.getDate("period_to").toLocalDate(),
                        rs.getString("settlement_status"));
        return new Row(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class), rs.getString("owner_name"),
                rs.getString("owner_email"), settlement, rs.getBigDecimal("amount"), rs.getBigDecimal("courtly_offset_amount"),
                rs.getDate("paid_on").toLocalDate(), rs.getString("reference_code"), rs.getString("proof_url"), rs.getString("note"),
                rs.getString("status"), rs.getString("review_note"), rs.getString("reviewer"), instant(rs, "reviewed_at"),
                instant(rs, "created_at"));
    }

    private List<OwnerOption> ownerOptions() {
        return jdbc.query("""
                SELECT u.id, u.full_name, u.email FROM users u
                WHERE EXISTS (SELECT 1 FROM court_owner_profiles p WHERE p.user_id = u.id) ORDER BY u.full_name, u.email LIMIT 500
                """, (rs, index) -> new OwnerOption(rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("email")));
    }

    private boolean exists(String table, UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = ?)", Boolean.class, id));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
