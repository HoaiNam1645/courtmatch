package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminDisputeResponses.VenueStat;
import com.courtly.api.admin.dto.AdminDisputeResponses.VenueStatsResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.service.booking.AvailabilityService;
import com.courtly.service.dispute.DisputeVenueSuspensionService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.103 - thong ke bao cao don dat san theo tung san de admin can nhac (chi doc; tam ngung la 2.3.104). Bo tai khoan mau @bwf.example. */
@Service
@RequiredArgsConstructor
public class AdminDisputeVenueStatsService {

    private static final String LOST = "(d.status = 'resolved' AND d.resolution IN ('refund_full', 'refund_half', 'owner_no_response'))";

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;
    private final DisputeVenueSuspensionService suspensionService;

    @Transactional(readOnly = true)
    public VenueStatsResponse list(UUID adminId, LocalDate from, LocalDate to, String q, String sort, int page, int size) {
        accessService.requirePermission(adminId, AdminDisputeService.PERMISSION);
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Khoang ngay khong hop le.",
                    Map.of("to", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        String order = switch (sort == null || sort.isBlank() ? "lost" : sort) {
            case "lost" -> "lost DESC, total DESC";
            case "total" -> "total DESC, lost DESC";
            case "rate" -> "lost_rate DESC, total DESC";
            case "refund" -> "refund_amount DESC, lost DESC";
            default -> throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Cach sap xep khong hop le.",
                    Map.of("sort", "Chi nhan lost, total, rate hoac refund"));
        };
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("o.email NOT LIKE '%@bwf.example'");
        if (from != null) {
            where.append(" AND d.created_at >= ?");
            args.add(Timestamp.from(from.atStartOfDay(AvailabilityService.ZONE).toInstant()));
        }
        if (to != null) {
            where.append(" AND d.created_at < ?");
            args.add(Timestamp.from(to.plusDays(1).atStartOfDay(AvailabilityService.ZONE).toInstant()));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" AND (lower(v.name) LIKE ? OR lower(o.full_name) LIKE ?)");
            args.add(like);
            args.add(like);
        }
        String from_ = " FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id JOIN venues v ON v.id = b.venue_id JOIN users o ON o.id = v.owner_id WHERE " + where;
        Long total = jdbc.queryForObject("SELECT count(DISTINCT v.id)" + from_, Long.class, args.toArray());
        Timestamp windowStart = Timestamp.from(Instant.now().minus(Duration.ofDays(suspensionService.windowDays())));
        List<Object> pageArgs = new ArrayList<>();
        pageArgs.add(windowStart);
        pageArgs.addAll(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<VenueStat> items = jdbc.query("SELECT * FROM (SELECT v.id, v.name, o.full_name AS owner_name, v.status, v.suspension_reason, count(*) AS total,"
                + " count(*) FILTER (WHERE d.status IN ('open', 'awaiting_owner_response')) AS awaiting,"
                + " count(*) FILTER (WHERE d.status = 'reviewing') AS pending,"
                + " count(*) FILTER (WHERE " + LOST + ") AS lost,"
                + " count(*) FILTER (WHERE d.status = 'rejected') AS rejected,"
                + " count(*) FILTER (WHERE d.status = 'cancelled') AS cancelled,"
                + " count(*) FILTER (WHERE d.status = 'resolved' AND d.handled_by IS NULL) AS auto_resolved,"
                + " round(100.0 * count(*) FILTER (WHERE " + LOST + ") / count(*), 1) AS lost_rate,"
                + " COALESCE(sum((SELECT sum(rf.amount) FROM refunds rf WHERE rf.dispute_id = d.id AND rf.status NOT IN ('rejected', 'failed'))), 0) AS refund_amount,"
                + " count(*) FILTER (WHERE " + LOST + " AND d.handled_at > ?) AS lost_window"
                + from_ + " GROUP BY v.id, v.name, o.full_name, v.status, v.suspension_reason) s ORDER BY " + order + ", name LIMIT ? OFFSET ?",
                (rs, row) -> new VenueStat(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("owner_name"), rs.getString("status"),
                        rs.getString("suspension_reason"), rs.getLong("total"), rs.getLong("awaiting"), rs.getLong("pending"), rs.getLong("lost"),
                        rs.getLong("rejected"), rs.getLong("cancelled"), rs.getLong("auto_resolved"), rs.getDouble("lost_rate"),
                        rs.getBigDecimal("refund_amount"), rs.getLong("lost_window")), pageArgs.toArray());
        return new VenueStatsResponse(items, page, size, total, (int) Math.ceil(total / (double) size),
                suspensionService.threshold(), suspensionService.windowDays());
    }
}
