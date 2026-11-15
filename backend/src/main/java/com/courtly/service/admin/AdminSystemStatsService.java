package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminSystemStatsResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
 * 2.3.38 - thong ke he thong. Tinh truc tiep tu du lieu goc, khong luu bang thong ke rieng;
 * ngay tinh theo gio Viet Nam, khoang khong co du lieu hien 0.
 */
@Service
@RequiredArgsConstructor
public class AdminSystemStatsService {

    static final String PERMISSION = "stats.system.view";
    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<String> USER_STATUSES = List.of("active", "inactive", "banned", "pending");
    private static final List<String> VENUE_APPROVALS = List.of("pending", "approved", "rejected");
    private static final List<String> VENUE_STATUSES = List.of("pending", "active", "inactive", "rejected");
    private static final List<String> BOOKING_STATUSES = List.of("pending_owner_confirmation", "awaiting_payment", "confirmed",
            "rejected", "cancelled", "completed", "expired", "disputed", "refunded");
    private static final List<String> MATCH_STATUSES = List.of("scheduled", "completed", "disputed", "cancelled");
    private static final List<String> REPORT_TYPES = List.of("user", "venue", "venue_review", "partner_review");

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;

    /**
     * @param from        ngay bat dau (tinh ca ngay nay); null = 29 ngay truoc ngay ket thuc
     * @param to          ngay ket thuc (tinh ca ngay nay); null = hom nay
     * @param granularity day hoac month
     */
    @Transactional(readOnly = true)
    public AdminSystemStatsResponse get(UUID adminId, LocalDate from, LocalDate to, String granularity) {
        accessService.requirePermission(adminId, PERMISSION);
        LocalDate end = to == null ? LocalDate.now(ZONE) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
        boolean monthly = parseGranularity(granularity);
        validate(start, end);
        return new AdminSystemStatsResponse(overview(), trend(start, end, monthly));
    }

    private AdminSystemStatsResponse.Overview overview() {
        LocalDate today = LocalDate.now(ZONE);
        Instant todayStart = today.atStartOfDay(ZONE).toInstant();
        Map<String, Long> byStatus = counts("SELECT status, count(*) FROM users GROUP BY status", USER_STATUSES);
        Map<String, Long> byRole = counts("""
                SELECT r.code, count(DISTINCT ur.user_id) FROM user_roles ur JOIN roles r ON r.id = ur.role_id GROUP BY r.code
                """, jdbc.queryForList("SELECT code FROM roles ORDER BY code", String.class));
        long totalUsers = byStatus.values().stream().mapToLong(Long::longValue).sum();
        AdminSystemStatsResponse.Users users = new AdminSystemStatsResponse.Users(totalUsers, byRole, byStatus,
                countSince("users", todayStart), countSince("users", today.minusDays(6).atStartOfDay(ZONE).toInstant()),
                countSince("users", today.minusDays(29).atStartOfDay(ZONE).toInstant()));

        Map<String, Long> approvals = counts("SELECT approval_status, count(*) FROM venues GROUP BY approval_status", VENUE_APPROVALS);
        Map<String, Long> venueStatuses = counts("SELECT status, count(*) FROM venues GROUP BY status", VENUE_STATUSES);
        AdminSystemStatsResponse.Venues venues = new AdminSystemStatsResponse.Venues(sum(approvals), approvals, venueStatuses);

        Map<String, Long> bookings = counts("SELECT status, count(*) FROM bookings GROUP BY status", BOOKING_STATUSES);
        Map<String, Long> matches = counts("SELECT status, count(*) FROM matches GROUP BY status", MATCH_STATUSES);

        Map<String, Long> open = counts("""
                SELECT target_type, count(*) FROM reports WHERE status IN ('pending', 'reviewing') GROUP BY target_type
                """, REPORT_TYPES);
        long pending = jdbc.queryForObject("SELECT count(*) FROM reports WHERE status = 'pending'", Long.class);
        long reviewing = jdbc.queryForObject("SELECT count(*) FROM reports WHERE status = 'reviewing'", Long.class);

        return new AdminSystemStatsResponse.Overview(users, venues,
                new AdminSystemStatsResponse.Counted(sum(bookings), bookings),
                new AdminSystemStatsResponse.Counted(sum(matches), matches),
                new AdminSystemStatsResponse.Reports(pending, reviewing, open));
    }

    private AdminSystemStatsResponse.Trend trend(LocalDate start, LocalDate end, boolean monthly) {
        Instant from = start.atStartOfDay(ZONE).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(ZONE).toInstant();
        Map<LocalDate, Long> users = series("users", monthly, from, to);
        Map<LocalDate, Long> venues = series("venues", monthly, from, to);
        Map<LocalDate, Long> bookings = series("bookings", monthly, from, to);
        Map<LocalDate, Long> matches = series("matches", monthly, from, to);

        List<AdminSystemStatsResponse.Point> points = new ArrayList<>();
        LocalDate period = monthly ? start.withDayOfMonth(1) : start;
        while (!period.isAfter(end)) {
            points.add(new AdminSystemStatsResponse.Point(period, users.getOrDefault(period, 0L), venues.getOrDefault(period, 0L),
                    bookings.getOrDefault(period, 0L), matches.getOrDefault(period, 0L)));
            period = monthly ? period.plusMonths(1) : period.plusDays(1);
        }
        AdminSystemStatsResponse.Point totals = new AdminSystemStatsResponse.Point(null,
                points.stream().mapToLong(AdminSystemStatsResponse.Point::newUsers).sum(),
                points.stream().mapToLong(AdminSystemStatsResponse.Point::newVenues).sum(),
                points.stream().mapToLong(AdminSystemStatsResponse.Point::newBookings).sum(),
                points.stream().mapToLong(AdminSystemStatsResponse.Point::newMatches).sum());
        return new AdminSystemStatsResponse.Trend(start, end, monthly ? "month" : "day", points, totals);
    }

    /** Dem ban ghi tao moi theo ngay / thang (gio Viet Nam) trong [from, to). Ten bang chi lay tu danh sach co dinh. */
    private Map<LocalDate, Long> series(String table, boolean monthly, Instant from, Instant to) {
        String bucket = monthly
                ? "date_trunc('month', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date"
                : "(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date";
        Map<LocalDate, Long> result = new LinkedHashMap<>();
        jdbc.query("SELECT " + bucket + " AS period, count(*) AS total FROM " + table
                        + " WHERE created_at >= ? AND created_at < ? GROUP BY period",
                rs -> {
                    result.put(rs.getDate("period").toLocalDate(), rs.getLong("total"));
                }, Timestamp.from(from), Timestamp.from(to));
        return result;
    }

    private long countSince(String table, Instant since) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE created_at >= ?", Long.class, Timestamp.from(since));
        return total == null ? 0 : total;
    }

    /** Du moi khoa trong {@code keys} (0 neu chua co), giu them khoa la trong du lieu. */
    private Map<String, Long> counts(String sql, List<String> keys) {
        Map<String, Long> result = new LinkedHashMap<>();
        keys.forEach(key -> result.put(key, 0L));
        jdbc.query(sql, rs -> {
            result.put(rs.getString(1), rs.getLong(2));
        });
        return result;
    }

    private static long sum(Map<String, Long> values) {
        return values.values().stream().mapToLong(Long::longValue).sum();
    }

    private static boolean parseGranularity(String granularity) {
        if (granularity == null || granularity.isBlank() || "day".equals(granularity)) {
            return false;
        }
        if ("month".equals(granularity)) {
            return true;
        }
        throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Don vi thoi gian khong hop le.",
                Map.of("granularity", "Chi nhan day hoac month"));
    }

    private static void validate(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ngay ket thuc phai sau hoac bang ngay bat dau.",
                    Map.of("to", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Chi xem toi da " + MAX_DAYS + " ngay moi lan.",
                    Map.of("to", "Khoang thoi gian toi da " + MAX_DAYS + " ngay"));
        }
    }
}
