package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminPaymentResponses.ListResponse;
import com.courtly.api.admin.dto.AdminPaymentResponses.OwnerOption;
import com.courtly.api.admin.dto.AdminPaymentResponses.OwnerSummary;
import com.courtly.api.admin.dto.AdminPaymentResponses.Row;
import com.courtly.api.admin.dto.AdminPaymentResponses.Settlement;
import com.courtly.api.admin.dto.AdminPaymentResponses.Totals;
import com.courtly.api.admin.dto.RecordPaymentRequest;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.service.audit.AuditService;
import com.courtly.service.finance.CommissionAllocator;
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
                   r.status, r.review_note, rv.full_name AS reviewer, r.reviewed_at, r.created_at, (r.recorded_by IS NOT NULL) AS recorded
            FROM commission_payment_reports r
            JOIN users u ON u.id = r.owner_id
            LEFT JOIN users rv ON rv.id = r.reviewed_by
            LEFT JOIN commission_settlements cs ON cs.id = r.settlement_id
            """;

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;
    private final OwnerRestrictionService restrictionService;
    private final CommissionAllocator allocator;

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
        return new ListResponse(rows, page, size, totals.count(), totalPages, totals, statusCounts, ownerOptions(),
                ownerId == null ? null : ownerSummary(ownerId));
    }

    /** 2.3.50 - tong quan cong no va thanh toan cua mot chu san, khong phu thuoc bo loc dang chon. */
    private OwnerSummary ownerSummary(UUID ownerId) {
        return jdbc.queryForObject("""
                SELECT u.full_name,
                       (SELECT coalesce(sum(amount), 0) FROM commission_payment_reports WHERE owner_id = u.id AND status = 'confirmed') AS confirmed,
                       (SELECT coalesce(sum(amount), 0) FROM commission_payment_reports WHERE owner_id = u.id AND status = 'pending_review') AS pending,
                       (SELECT coalesce(sum(commission_amount), 0) FROM booking_commissions
                         WHERE owner_id = u.id AND status IN ('pending', 'due', 'adjusted')) AS outstanding,
                       coalesce(p.commission_credit, 0) AS credit,
                       (SELECT coalesce(sum(courtly_owed), 0) FROM booking_commissions
                         WHERE owner_id = u.id AND courtly_owed > 0 AND courtly_owed_cleared_at IS NULL
                           AND status NOT IN ('waived', 'suspended')) AS owed,
                       coalesce(p.restriction_level, 0) AS restriction
                FROM users u LEFT JOIN court_owner_profiles p ON p.user_id = u.id
                WHERE u.id = ?
                """, (rs, index) -> new OwnerSummary(ownerId, rs.getString("full_name"), rs.getBigDecimal("confirmed"),
                rs.getBigDecimal("pending"), rs.getBigDecimal("outstanding"), rs.getBigDecimal("credit"), rs.getBigDecimal("owed"),
                rs.getInt("restriction")), ownerId);
    }

    /** 2.3.47 - xac nhan chu san da thanh toan cong no. */
    @Transactional
    public Row confirm(UUID adminId, UUID reportId, String note) {
        User admin = accessService.requirePermission(adminId, MANAGE);
        Report report = lockPending(reportId);
        Instant now = Instant.now();
        jdbc.queryForObject("SELECT user_id FROM court_owner_profiles WHERE user_id = ? FOR UPDATE", UUID.class, report.ownerId);

        Scope scope = scopeOf(report.ownerId, report.settlementId, report.amount);
        BigDecimal credit = scope.credit;
        BigDecimal remaining = scope.remaining;
        BigDecimal paidBefore = scope.paid;
        BigDecimal due = scope.due;
        String settlementStatus = scope.settlementStatus;

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
            settled = allocator.settleForSettlement(report.settlementId, paidAfter, fullyPaid, now);
            newSettlementStatus = fullyPaid ? "paid" : "partial";
            jdbc.update("""
                    UPDATE commission_settlements SET amount_paid = ?, status = ?, paid_at = ?, reviewed_by = ?, reviewed_at = ?,
                           payment_reference = ?, proof_url = coalesce(?, proof_url) WHERE id = ?
                    """, paidAfter, newSettlementStatus, fullyPaid ? Timestamp.from(now) : null, admin.getId(), Timestamp.from(now),
                    report.referenceCode, report.proofUrl, report.settlementId);
        } else {
            CommissionAllocator.CreditResult result = allocator.applyCredit(report.ownerId, credit.add(pool), now);
            settled = result.settled();
            creditAfter = result.creditLeft();
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

    /**
     * 2.3.49 - quan tri vien ghi nhan giao dich doi soat: chu san da tra ngoai he thong (tien mat, chuyen khoan truc tiep) nen
     * khong co bao cao. Tao bao cao da duoc xac nhan va ap dung cung quy tac voi {@link #confirm}; that bai thi khong ghi gi.
     */
    @Transactional
    public Row record(UUID adminId, RecordPaymentRequest request) {
        User admin = accessService.requirePermission(adminId, MANAGE);
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        if (request.paidOn().isAfter(today)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ngay thanh toan khong duoc o tuong lai.",
                    Map.of("paidOn", "Ngay thanh toan khong duoc sau hom nay"));
        }
        if (request.paidOn().isBefore(today.minusDays(365))) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ngay thanh toan qua xa.",
                    Map.of("paidOn", "Chi ghi nhan thanh toan trong 365 ngay gan day"));
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM court_owner_profiles WHERE user_id = ?)",
                Boolean.class, request.ownerId()))) {
            throw ApiException.notFound("Khong tim thay chu san.");
        }
        if (request.settlementId() != null) {
            List<UUID> owners = jdbc.query("SELECT owner_id FROM commission_settlements WHERE id = ?",
                    (rs, index) -> rs.getObject("owner_id", UUID.class), request.settlementId());
            if (owners.isEmpty()) {
                throw ApiException.notFound("Khong tim thay ky doi soat.");
            }
            if (!owners.getFirst().equals(request.ownerId())) {
                throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ky doi soat khong thuoc chu san nay.",
                        Map.of("settlementId", "Ky doi soat khong thuoc chu san da chon"));
            }
        }
        String reference = request.referenceCode().trim();
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM commission_payment_reports
                               WHERE owner_id = ? AND lower(reference_code) = lower(?) AND status <> 'rejected')
                """, Boolean.class, request.ownerId(), reference))) {
            throw ApiException.conflict(ErrorCode.COMMISSION_PAYMENT_INVALID, "Ma tham chieu nay da duoc dung cho mot lan thanh toan khac.",
                    Map.of("referenceCode", "Ma tham chieu da duoc su dung"));
        }

        // Kiem tra so con no truoc khi ghi: that bai thi khong tao ban ghi nao.
        scopeOf(request.ownerId(), request.settlementId(), request.amount());

        UUID id = UUID.randomUUID();
        String proof = request.proofUrl() == null || request.proofUrl().isBlank() ? null : request.proofUrl().trim();
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        try {
            jdbc.update("""
                    INSERT INTO commission_payment_reports (id, owner_id, settlement_id, amount, paid_on, reference_code, proof_url, note,
                                                            status, recorded_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending_review', ?)
                    """, id, request.ownerId(), request.settlementId(), request.amount(), java.sql.Date.valueOf(request.paidOn()),
                    reference, proof, note, admin.getId());
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            // Hai quan tri vien ghi cung ma tham chieu cung luc: rang buoc duy nhat o database chan lai.
            throw ApiException.conflict(ErrorCode.COMMISSION_PAYMENT_INVALID, "Ma tham chieu nay da duoc dung cho mot lan thanh toan khac.",
                    Map.of("referenceCode", "Ma tham chieu da duoc su dung"));
        }
        Row confirmed = confirm(adminId, id, null);
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("reportId", id.toString());
        audit.put("ownerId", request.ownerId().toString());
        audit.put("referenceCode", reference);
        audit.put("amount", request.amount().toPlainString());
        audit.put("paidOn", request.paidOn().toString());
        if (request.settlementId() != null) {
            audit.put("settlementId", request.settlementId().toString());
        }
        auditService.record(admin, "settlement.payment.record", "commission_payment_reports", id, null, audit);
        return confirmed;
    }

    /**
     * 2.3.48 - tu choi bao cao thanh toan hoac yeu cau bo sung bang chung.
     *
     * <p>Ly do bat buoc. Cong no khong doi; ma tham chieu cua bao cao bi tu choi duoc dung lai khi chu san gui bao cao moi.
     * Ky chi quay lai trang thai truoc khi gui neu khong con bao cao nao khac dang cho xac nhan.
     */
    @Transactional
    public Row reject(UUID adminId, UUID reportId, String reason) {
        User admin = accessService.requirePermission(adminId, MANAGE);
        Report report = lockPending(reportId);
        Instant now = Instant.now();
        String cleanReason = reason.trim();
        if (cleanReason.length() < 5) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ly do tu choi qua ngan.",
                    Map.of("reason", "Ly do tu 5 den 500 ky tu"));
        }
        jdbc.update("""
                UPDATE commission_payment_reports SET status = 'rejected', reviewed_by = ?, reviewed_at = ?, review_note = ? WHERE id = ?
                """, admin.getId(), Timestamp.from(now), cleanReason, reportId);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("reportId", reportId.toString());
        audit.put("ownerId", report.ownerId.toString());
        audit.put("referenceCode", report.referenceCode);
        audit.put("amount", report.amount.toPlainString());
        audit.put("reason", cleanReason);
        Map<String, Object> before = Map.of("status", "pending_review");
        auditService.record(admin, "settlement.payment.reject", "commission_payment_reports", reportId, before, audit);

        if (report.settlementId != null) {
            Map<String, Object> settlement = jdbc.queryForMap(
                    "SELECT amount_paid, status FROM commission_settlements WHERE id = ? FOR UPDATE", report.settlementId);
            String current = (String) settlement.get("status");
            String restored = current;
            Long stillPending = jdbc.queryForObject(
                    "SELECT count(*) FROM commission_payment_reports WHERE settlement_id = ? AND status = 'pending_review'",
                    Long.class, report.settlementId);
            if ("pending_review".equals(current) && stillPending == 0) {
                BigDecimal paid = (BigDecimal) settlement.get("amount_paid");
                boolean overdue = Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM booking_commissions WHERE settlement_id = ?
                                       AND status IN ('pending', 'due', 'adjusted') AND due_at < now())
                        """, Boolean.class, report.settlementId));
                restored = paid.signum() > 0 ? "partial" : overdue ? "overdue" : "open";
                jdbc.update("UPDATE commission_settlements SET status = ?, reviewed_by = ?, reviewed_at = ? WHERE id = ?",
                        restored, admin.getId(), Timestamp.from(now), report.settlementId);
            }
            Map<String, Object> settlementAudit = new LinkedHashMap<>(audit);
            settlementAudit.put("settlementStatus", restored);
            auditService.record(admin, "settlement.payment.reject", "commission_settlement", report.settlementId,
                    Map.of("status", current), settlementAudit);
        }
        return jdbc.queryForObject(ROW_SELECT + " WHERE r.id = ?", (rs, index) -> row(rs), reportId);
    }

    /** Cong no con lai cua pham vi (ky hoac cong no chung) va trang thai de kiem tra truoc khi ap dung mot khoan thanh toan. */
    private record Scope(BigDecimal remaining, BigDecimal due, BigDecimal paid, String settlementStatus, BigDecimal credit) {
    }

    /**
     * Khoa chu san va ky roi tinh so con no; nem 409 neu ky da thanh toan / dang tranh chap hoac so tien vuot so con no.
     * Goi truoc khi ghi bat ky thay doi nao de that bai khong de lai du lieu.
     */
    private Scope scopeOf(UUID ownerId, UUID settlementId, BigDecimal amount) {
        jdbc.queryForObject("SELECT user_id FROM court_owner_profiles WHERE user_id = ? FOR UPDATE", UUID.class, ownerId);
        BigDecimal credit = jdbc.queryForObject("SELECT commission_credit FROM court_owner_profiles WHERE user_id = ?", BigDecimal.class, ownerId);
        BigDecimal remaining;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal due = BigDecimal.ZERO;
        String settlementStatus = null;
        if (settlementId != null) {
            Map<String, Object> settlement = jdbc.queryForMap(
                    "SELECT amount_due, amount_paid, status FROM commission_settlements WHERE id = ? FOR UPDATE", settlementId);
            due = (BigDecimal) settlement.get("amount_due");
            paid = (BigDecimal) settlement.get("amount_paid");
            settlementStatus = (String) settlement.get("status");
            if ("paid".equals(settlementStatus)) {
                throw conflict("Ky doi soat nay da thanh toan xong.");
            }
            if ("disputed".equals(settlementStatus)) {
                throw conflict("Ky doi soat dang tranh chap, chua the xac nhan thanh toan.");
            }
            remaining = due.subtract(paid);
        } else {
            BigDecimal debt = jdbc.queryForObject("""
                    SELECT coalesce(sum(commission_amount), 0) FROM booking_commissions
                    WHERE owner_id = ? AND settlement_id IS NULL AND status IN ('pending', 'due', 'adjusted')
                    """, BigDecimal.class, ownerId);
            remaining = debt.subtract(credit);
        }
        if (amount.compareTo(remaining) > 0) {
            throw conflict("So tien bao cao vuot qua so con no (" + remaining.max(BigDecimal.ZERO).stripTrailingZeros().toPlainString()
                    + " dong). Hay tu choi bao cao hoac dieu chinh cong no truoc.");
        }
        return new Scope(remaining, due, paid, settlementStatus, credit);
    }

    private Report lockPending(UUID reportId) {
        List<Report> found = jdbc.query("""
                SELECT id, owner_id, settlement_id, amount, reference_code, proof_url, status FROM commission_payment_reports WHERE id = ? FOR UPDATE
                """, (rs, index) -> new Report(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
                rs.getObject("settlement_id", UUID.class), rs.getBigDecimal("amount"), rs.getString("reference_code"),
                rs.getString("proof_url"), rs.getString("status")), reportId);
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

    private record Report(UUID id, UUID ownerId, UUID settlementId, BigDecimal amount, String referenceCode, String proofUrl,
                          String status) {
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
                instant(rs, "created_at"), rs.getBoolean("recorded"));
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
