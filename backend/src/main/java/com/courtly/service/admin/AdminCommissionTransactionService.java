package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminCommissionTransactionResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.43 - quan tri vien xem danh sach giao dich doi soat (hoa hong tung booking).
 *
 * <p>Chi doc, tinh truc tiep tu booking_commissions. Qua han = chua thu (pending, due, adjusted) va da
 * qua han thanh toan, cung quy tac voi man hinh cong no cua chu san.
 */
@Service
@RequiredArgsConstructor
public class AdminCommissionTransactionService {

    static final String PERMISSION = "finance.commission.view";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> STATUSES = Set.of("pending", "due", "adjusted", "suspended", "waived", "settled");

    private static final String FROM_CLAUSE = """
             FROM booking_commissions bc
             JOIN bookings b ON b.id = bc.booking_id
             JOIN venues v ON v.id = b.venue_id
             JOIN users u ON u.id = bc.owner_id
             LEFT JOIN commission_settlements cs ON cs.id = bc.settlement_id
            """;

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;

    /**
     * @param q            tim theo ma booking (khong phan biet hoa thuong)
     * @param status       all hoac mot trang thai hoa hong
     * @param venueId      null = moi san
     * @param settlementId null = moi ky doi soat
     * @param from         ngay ghi nhan bat dau (gio Viet Nam, tinh ca ngay nay)
     * @param to           ngay ghi nhan ket thuc (tinh ca ngay nay)
     * @param overdueOnly  chi lay khoan qua han
     */
    @Transactional(readOnly = true)
    public AdminCommissionTransactionResponse list(UUID adminId, String q, String status, UUID venueId, UUID settlementId,
                                                   LocalDate from, LocalDate to, boolean overdueOnly, int page, int size) {
        accessService.requirePermission(adminId, PERMISSION);
        if (status != null && !status.isBlank() && !"all".equals(status) && !STATUSES.contains(status)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai hoa hong khong hop le.",
                    Map.of("status", "Chi nhan all, pending, due, adjusted, suspended, waived hoac settled"));
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ngay ket thuc phai sau hoac bang ngay bat dau.",
                    Map.of("to", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        if (venueId != null && !exists("venues", venueId)) {
            throw ApiException.notFound("Khong tim thay san.");
        }
        if (settlementId != null && !exists("commission_settlements", settlementId)) {
            throw ApiException.notFound("Khong tim thay ky doi soat.");
        }

        Timestamp now = Timestamp.from(Instant.now());
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            where.append(" AND lower(b.booking_code) LIKE ? ESCAPE '\\'");
            args.add("%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (status != null && !status.isBlank() && !"all".equals(status)) {
            where.append(" AND bc.status = ?");
            args.add(status);
        }
        if (venueId != null) {
            where.append(" AND v.id = ?");
            args.add(venueId);
        }
        if (settlementId != null) {
            where.append(" AND bc.settlement_id = ?");
            args.add(settlementId);
        }
        if (from != null) {
            where.append(" AND bc.calculated_at >= ?");
            args.add(Timestamp.from(from.atStartOfDay(ZONE).toInstant()));
        }
        if (to != null) {
            where.append(" AND bc.calculated_at < ?");
            args.add(Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant()));
        }
        if (overdueOnly) {
            where.append(" AND bc.status IN ('pending', 'due', 'adjusted') AND bc.due_at < ?");
            args.add(now);
        }

        AdminCommissionTransactionResponse.Totals totals = jdbc.queryForObject(
                "SELECT count(*) AS total, coalesce(sum(bc.gross_amount), 0) AS gross, coalesce(sum(bc.commission_amount), 0) AS amount"
                        + FROM_CLAUSE + where,
                (rs, row) -> new AdminCommissionTransactionResponse.Totals(rs.getLong("total"), rs.getBigDecimal("gross"),
                        rs.getBigDecimal("amount")), args.toArray());

        List<Object> pageArgs = new ArrayList<>();
        pageArgs.add(now);
        pageArgs.addAll(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<AdminCommissionTransactionResponse.Row> rows = jdbc.query("""
                SELECT bc.id AS commission_id, b.id AS booking_id, b.booking_code, v.id AS venue_id, v.name AS venue_name,
                       u.full_name AS owner_name, bc.gross_amount, bc.commission_amount, bc.status, bc.calculated_at, bc.due_at,
                       bc.settled_at, (bc.status IN ('pending', 'due', 'adjusted') AND bc.due_at < ?) AS overdue,
                       cs.id AS settlement_id, cs.period_from, cs.period_to, cs.status AS settlement_status
                """ + FROM_CLAUSE + where + " ORDER BY bc.calculated_at DESC, bc.id DESC LIMIT ? OFFSET ?",
                (rs, index) -> new AdminCommissionTransactionResponse.Row(rs.getObject("commission_id", UUID.class),
                        rs.getObject("booking_id", UUID.class), rs.getString("booking_code"), rs.getObject("venue_id", UUID.class),
                        rs.getString("venue_name"), rs.getString("owner_name"), rs.getBigDecimal("gross_amount"),
                        rs.getBigDecimal("commission_amount"), rs.getString("status"),
                        rs.getTimestamp("calculated_at").toInstant(), rs.getTimestamp("due_at").toInstant(), rs.getBoolean("overdue"),
                        rs.getObject("settlement_id") == null ? null
                                : new AdminCommissionTransactionResponse.Settlement(rs.getObject("settlement_id", UUID.class),
                                        rs.getDate("period_from").toLocalDate(), rs.getDate("period_to").toLocalDate(),
                                        rs.getString("settlement_status")),
                        rs.getTimestamp("settled_at") == null ? null : rs.getTimestamp("settled_at").toInstant()),
                pageArgs.toArray());

        int totalPages = (int) Math.ceil(totals.count() / (double) size);
        return new AdminCommissionTransactionResponse(rows, page, size, totals.count(), totalPages, totals, venueOptions(), settlementOptions());
    }

    private boolean exists(String table, UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = ?)", Boolean.class, id));
    }

    private List<AdminCommissionTransactionResponse.VenueOption> venueOptions() {
        return jdbc.query("SELECT id, name FROM venues ORDER BY name",
                (rs, row) -> new AdminCommissionTransactionResponse.VenueOption(rs.getObject("id", UUID.class), rs.getString("name")));
    }

    private List<AdminCommissionTransactionResponse.SettlementOption> settlementOptions() {
        return jdbc.query("""
                SELECT cs.id, u.full_name, cs.period_from, cs.period_to, cs.status
                FROM commission_settlements cs JOIN users u ON u.id = cs.owner_id
                ORDER BY cs.period_to DESC, cs.created_at DESC LIMIT 200
                """, (rs, row) -> new AdminCommissionTransactionResponse.SettlementOption(rs.getObject("id", UUID.class),
                rs.getString("full_name"), rs.getDate("period_from").toLocalDate(), rs.getDate("period_to").toLocalDate(),
                rs.getString("status")));
    }
}
