package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminSettlementResponses;
import com.courtly.api.admin.dto.AdminSettlementResponses.Booking;
import com.courtly.api.admin.dto.AdminSettlementResponses.BookingPage;
import com.courtly.api.admin.dto.AdminSettlementResponses.Detail;
import com.courtly.api.admin.dto.AdminSettlementResponses.HistoryEntry;
import com.courtly.api.admin.dto.AdminSettlementResponses.ListResponse;
import com.courtly.api.admin.dto.AdminSettlementResponses.OwnerOption;
import com.courtly.api.admin.dto.AdminSettlementResponses.Report;
import com.courtly.api.admin.dto.AdminSettlementResponses.Row;
import com.courtly.api.admin.dto.AdminSettlementResponses.Totals;
import com.courtly.api.admin.dto.CreateSettlementRequest;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.service.audit.AuditService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.45 - 2.3.46: quan tri vien xem danh sach ky cong no hoa hong, chot ky moi va xem chi tiet mot ky.
 *
 * <p>Ky doi soat khong co so cai rieng: danh sach booking cua ky la cac hoa hong co {@code settlement_id}
 * tro toi ky, con so tien phai thu la tong hoa hong luc chot ky (cong dieu chinh neu co).
 */
@Service
@RequiredArgsConstructor
public class AdminSettlementService {

    static final String VIEW = "finance.commission.view";
    static final String MANAGE = "finance.commission.manage";
    static final List<String> STATUSES = List.of("open", "pending_review", "partial", "paid", "overdue", "disputed");
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String ROW_SELECT = """
            SELECT cs.id, cs.owner_id, u.full_name AS owner_name, u.email AS owner_email, cs.period_from, cs.period_to,
                   cs.commission_total, cs.adjustment_total, cs.amount_due, cs.amount_paid, cs.status, cs.created_at, cs.paid_at,
                   (SELECT count(*) FROM booking_commissions bc WHERE bc.settlement_id = cs.id) AS booking_count,
                   (SELECT count(*) FROM commission_payment_reports r WHERE r.settlement_id = cs.id AND r.status = 'pending_review') AS pending_reports,
                   (cs.amount_due > cs.amount_paid AND EXISTS (SELECT 1 FROM booking_commissions bc WHERE bc.settlement_id = cs.id
                       AND bc.status IN ('pending', 'due', 'adjusted') AND bc.due_at < now())) AS overdue
            FROM commission_settlements cs
            JOIN users u ON u.id = cs.owner_id
            """;

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * @param q       tim theo ten hoac email chu san
     * @param status  all hoac mot trang thai ky
     * @param ownerId null = moi chu san
     * @param from    ky co ngay ket thuc tu ngay nay tro di
     * @param to      ky co ngay bat dau khong sau ngay nay
     */
    @Transactional(readOnly = true)
    public ListResponse list(UUID adminId, String q, String status, UUID ownerId, LocalDate from, LocalDate to,
                             boolean overdueOnly, int page, int size) {
        accessService.requirePermission(adminId, VIEW);
        if (status != null && !status.isBlank() && !"all".equals(status) && !STATUSES.contains(status)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai ky doi soat khong hop le.",
                    Map.of("status", "Chi nhan all hoac " + String.join(", ", STATUSES)));
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ngay ket thuc phai sau hoac bang ngay bat dau.",
                    Map.of("to", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        if (ownerId != null && !exists("users", ownerId)) {
            throw ApiException.notFound("Khong tim thay chu san.");
        }

        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            where.append(" AND (lower(u.full_name) LIKE ? ESCAPE '\\' OR lower(u.email) LIKE ? ESCAPE '\\')");
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like);
            args.add(like);
        }
        if (ownerId != null) {
            where.append(" AND cs.owner_id = ?");
            args.add(ownerId);
        }
        if (from != null) {
            where.append(" AND cs.period_to >= ?");
            args.add(java.sql.Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND cs.period_from <= ?");
            args.add(java.sql.Date.valueOf(to));
        }
        if (overdueOnly) {
            where.append(" AND cs.amount_due > cs.amount_paid AND EXISTS (SELECT 1 FROM booking_commissions bc WHERE bc.settlement_id = cs.id"
                    + " AND bc.status IN ('pending', 'due', 'adjusted') AND bc.due_at < now())");
        }

        // Dem theo trang thai: moi bo loc khac, tru trang thai.
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        STATUSES.forEach(value -> statusCounts.put(value, 0L));
        jdbc.query("SELECT cs.status AS status, count(*) AS total FROM commission_settlements cs JOIN users u ON u.id = cs.owner_id"
                + where + " GROUP BY cs.status", rs -> {
            statusCounts.put(rs.getString("status"), rs.getLong("total"));
        }, args.toArray());

        List<Object> filteredArgs = new ArrayList<>(args);
        String filteredWhere = where.toString();
        if (status != null && !status.isBlank() && !"all".equals(status)) {
            filteredWhere += " AND cs.status = ?";
            filteredArgs.add(status);
        }

        Totals totals = jdbc.queryForObject("""
                SELECT count(*) AS total, coalesce(sum(cs.amount_due), 0) AS due, coalesce(sum(cs.amount_paid), 0) AS paid,
                       coalesce(sum(greatest(cs.amount_due - cs.amount_paid, 0)), 0) AS remaining
                FROM commission_settlements cs JOIN users u ON u.id = cs.owner_id
                """ + filteredWhere, (rs, row) -> new Totals(rs.getLong("total"), rs.getBigDecimal("due"),
                rs.getBigDecimal("paid"), rs.getBigDecimal("remaining")), filteredArgs.toArray());

        List<Object> pageArgs = new ArrayList<>(filteredArgs);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<Row> rows = jdbc.query(ROW_SELECT + filteredWhere
                + " ORDER BY (cs.status = 'pending_review') DESC, cs.period_to DESC, cs.created_at DESC, cs.id LIMIT ? OFFSET ?",
                (rs, index) -> row(rs), pageArgs.toArray());

        int totalPages = (int) Math.ceil(totals.count() / (double) size);
        return new ListResponse(rows, page, size, totals.count(), totalPages, totals, statusCounts, ownerOptions());
    }

    /** Chot ky: gom hoa hong chua doi soat cua chu san trong khoang ngay thanh mot ky moi. */
    @Transactional
    public Row create(UUID adminId, CreateSettlementRequest request) {
        User admin = accessService.requirePermission(adminId, MANAGE);
        LocalDate today = LocalDate.now(ZONE);
        if (request.periodTo().isBefore(request.periodFrom())) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ngay ket thuc phai sau hoac bang ngay bat dau.",
                    Map.of("periodTo", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        if (request.periodTo().isAfter(today)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Khong the chot ky cho ngay chua toi.",
                    Map.of("periodTo", "Ngay ket thuc khong duoc sau hom nay"));
        }
        if (request.periodFrom().isBefore(today.minusYears(2))) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Khoang thoi gian qua xa.",
                    Map.of("periodFrom", "Chi chot ky trong 2 nam gan day"));
        }
        Boolean isOwner = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM court_owner_profiles WHERE user_id = ?)", Boolean.class,
                request.ownerId());
        if (!Boolean.TRUE.equals(isOwner)) {
            throw ApiException.notFound("Khong tim thay chu san.");
        }

        Timestamp start = Timestamp.from(request.periodFrom().atStartOfDay(ZONE).toInstant());
        Timestamp end = Timestamp.from(request.periodTo().plusDays(1).atStartOfDay(ZONE).toInstant());
        // Khoa cac hoa hong de hai quan tri vien chot ky cung luc khong gom trung mot booking.
        List<Object[]> commissions = jdbc.query("""
                SELECT id, commission_amount FROM booking_commissions
                WHERE owner_id = ? AND settlement_id IS NULL AND status IN ('pending', 'due', 'adjusted')
                  AND calculated_at >= ? AND calculated_at < ?
                FOR UPDATE
                """, (rs, index) -> new Object[] {rs.getObject("id", UUID.class), rs.getBigDecimal("commission_amount")},
                request.ownerId(), start, end);
        if (commissions.isEmpty()) {
            throw ApiException.conflict(ErrorCode.COMMISSION_PAYMENT_INVALID,
                    "Khong co hoa hong chua doi soat nao trong khoang thoi gian nay.",
                    Map.of("periodFrom", "Khong co hoa hong chua doi soat trong khoang da chon"));
        }
        BigDecimal total = commissions.stream().map(item -> (BigDecimal) item[1]).reduce(BigDecimal.ZERO, BigDecimal::add);

        UUID id = UUID.randomUUID();
        String note = request.note() == null || request.note().isBlank() ? null : request.note().trim();
        try {
            jdbc.update("""
                    INSERT INTO commission_settlements (id, owner_id, period_from, period_to, commission_total, amount_due, status, note)
                    VALUES (?, ?, ?, ?, ?, ?, 'open', ?)
                    """, id, request.ownerId(), java.sql.Date.valueOf(request.periodFrom()), java.sql.Date.valueOf(request.periodTo()),
                    total, total, note);
        } catch (DuplicateKeyException exception) {
            throw ApiException.conflict(ErrorCode.COMMISSION_PAYMENT_INVALID, "Chu san nay da co ky cung khoang thoi gian.",
                    Map.of("periodFrom", "Ky doi soat cung khoang thoi gian da ton tai"));
        }
        List<UUID> commissionIds = commissions.stream().map(item -> (UUID) item[0]).toList();
        for (UUID commissionId : commissionIds) {
            jdbc.update("UPDATE booking_commissions SET settlement_id = ? WHERE id = ?", id, commissionId);
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("ownerId", request.ownerId().toString());
        audit.put("periodFrom", request.periodFrom().toString());
        audit.put("periodTo", request.periodTo().toString());
        audit.put("bookings", commissionIds.size());
        audit.put("commissionTotal", total.toPlainString());
        auditService.record(admin, "settlement.create", "commission_settlement", id, null, audit);
        return jdbc.queryForObject(ROW_SELECT + " WHERE cs.id = ?", (rs, index) -> row(rs), id);
    }

    /** 2.3.46 - chi tiet mot ky: so tien, bao cao thanh toan, booking cau thanh, lich su. */
    @Transactional(readOnly = true)
    public Detail get(UUID adminId, UUID settlementId, int page, int size) {
        accessService.requirePermission(adminId, VIEW);
        List<Row> found = jdbc.query(ROW_SELECT + " WHERE cs.id = ?", (rs, index) -> row(rs), settlementId);
        if (found.isEmpty()) {
            throw ApiException.notFound("Khong tim thay ky doi soat.");
        }
        Row settlement = found.getFirst();

        Map<String, Object> ownerExtra = jdbc.queryForMap("""
                SELECT u.phone, coalesce(p.restriction_level, 0) AS restriction_level,
                       (SELECT coalesce(sum(courtly_owed), 0) FROM booking_commissions
                         WHERE owner_id = u.id AND courtly_owed > 0 AND courtly_owed_cleared_at IS NULL
                           AND status NOT IN ('waived', 'suspended')) AS owed
                FROM users u LEFT JOIN court_owner_profiles p ON p.user_id = u.id WHERE u.id = ?
                """, settlement.ownerId());

        List<Report> reports = jdbc.query("""
                SELECT r.id, r.amount, r.paid_on, r.reference_code, r.proof_url, r.note, r.status, r.review_note, r.reviewed_at,
                       r.created_at, rv.full_name AS reviewer
                FROM commission_payment_reports r LEFT JOIN users rv ON rv.id = r.reviewed_by
                WHERE r.settlement_id = ? ORDER BY r.created_at DESC
                """, (rs, index) -> new Report(rs.getObject("id", UUID.class), rs.getBigDecimal("amount"),
                rs.getDate("paid_on").toLocalDate(), rs.getString("reference_code"), rs.getString("proof_url"), rs.getString("note"),
                rs.getString("status"), rs.getString("review_note"), rs.getString("reviewer"), instant(rs, "reviewed_at"),
                instant(rs, "created_at")), settlementId);

        long totalBookings = settlement.bookingCount();
        List<Booking> bookings = jdbc.query("""
                SELECT bc.id, b.id AS booking_id, b.booking_code, v.name AS venue_name, bc.gross_amount, bc.commission_amount, bc.status,
                       bc.due_at, (bc.status IN ('pending', 'due', 'adjusted') AND bc.due_at < now()) AS overdue,
                       (b.status = 'completed'
                        AND EXISTS (SELECT 1 FROM payments p WHERE p.booking_id = b.id)
                        AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.booking_id = b.id
                                        AND p.status IN ('confirmed', 'partially_refunded', 'refunded'))) AS unconfirmed
                FROM booking_commissions bc
                JOIN bookings b ON b.id = bc.booking_id
                JOIN venues v ON v.id = b.venue_id
                WHERE bc.settlement_id = ?
                ORDER BY bc.due_at, b.booking_code LIMIT ? OFFSET ?
                """, (rs, index) -> new Booking(rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class),
                rs.getString("booking_code"), rs.getString("venue_name"), rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("commission_amount"), rs.getString("status"), instant(rs, "due_at"), rs.getBoolean("overdue"),
                rs.getBoolean("unconfirmed")), settlementId, size, (long) page * size);

        List<HistoryEntry> history = jdbc.query("""
                SELECT a.id, a.action, u.full_name AS actor_name, a.old_data::text AS old_data, a.new_data::text AS new_data, a.created_at
                FROM audit_logs a LEFT JOIN users u ON u.id = a.actor_id
                WHERE a.entity_type = 'commission_settlement' AND a.entity_id = ? ORDER BY a.created_at DESC
                """, (rs, index) -> new HistoryEntry(rs.getObject("id", UUID.class), rs.getString("action"), rs.getString("actor_name"),
                json(rs.getString("old_data")), json(rs.getString("new_data")), instant(rs, "created_at")), settlementId);

        int totalPages = (int) Math.ceil(totalBookings / (double) size);
        return new Detail(settlement, (String) ownerExtra.get("phone"), ((Number) ownerExtra.get("restriction_level")).intValue(),
                (BigDecimal) ownerExtra.get("owed"), reports, new BookingPage(bookings, page, size, totalBookings, totalPages), history);
    }

    private Row row(ResultSet rs) throws SQLException {
        BigDecimal due = rs.getBigDecimal("amount_due");
        BigDecimal paid = rs.getBigDecimal("amount_paid");
        return new Row(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class), rs.getString("owner_name"),
                rs.getString("owner_email"), rs.getDate("period_from").toLocalDate(), rs.getDate("period_to").toLocalDate(),
                rs.getBigDecimal("commission_total"), rs.getBigDecimal("adjustment_total"), due, paid,
                due.subtract(paid).max(BigDecimal.ZERO), rs.getString("status"), rs.getBoolean("overdue"),
                rs.getLong("booking_count"), rs.getLong("pending_reports"), instant(rs, "created_at"), instant(rs, "paid_at"));
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

    private Object json(String text) {
        if (text == null) {
            return null;
        }
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (JsonProcessingException e) {
            return text;
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
