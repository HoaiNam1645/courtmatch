package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminMatchingStatsResponse;
import com.courtly.api.admin.dto.AdminMatchingStatsResponse.Point;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.74 - thong ke hieu suat ghep cap. Tong hop truc tiep tu partner_requests, player_connections,
 * partner_suggestions, matching_runs va double_team_suggestions (khong co bang thong ke rieng); khoang khong co
 * du lieu hien 0. Du lieu mau (@bwf.example) khong duoc tinh.
 */
@Service
@RequiredArgsConstructor
public class AdminMatchingStatsService {

    static final String PERMISSION = "matching.view";

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;

    private enum Granularity {
        DAY("day", "(%s AT TIME ZONE 'Asia/Ho_Chi_Minh')::date"),
        WEEK("week", "date_trunc('week', %s AT TIME ZONE 'Asia/Ho_Chi_Minh')::date"),
        MONTH("month", "date_trunc('month', %s AT TIME ZONE 'Asia/Ho_Chi_Minh')::date");

        final String id;
        final String expression;

        Granularity(String id, String expression) {
            this.id = id;
            this.expression = expression;
        }

        String bucket(String column) {
            return expression.formatted(column);
        }

        LocalDate start(LocalDate date) {
            return switch (this) {
                case DAY -> date;
                case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case MONTH -> date.withDayOfMonth(1);
            };
        }

        LocalDate next(LocalDate period) {
            return switch (this) {
                case DAY -> period.plusDays(1);
                case WEEK -> period.plusWeeks(1);
                case MONTH -> period.plusMonths(1);
            };
        }

        static Granularity parse(String value) {
            if (value == null || value.isBlank()) return DAY;
            for (Granularity item : values()) {
                if (item.id.equals(value)) return item;
            }
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Don vi thoi gian khong hop le.",
                    Map.of("granularity", "Chi nhan day, week hoac month"));
        }
    }

    @Transactional(readOnly = true)
    public AdminMatchingStatsResponse get(UUID adminId, LocalDate from, LocalDate to, String granularityValue) {
        accessService.requirePermission(adminId, PERMISSION);
        MatchingRange range = MatchingRange.of(from, to);
        Granularity granularity = Granularity.parse(granularityValue);
        Timestamp start = Timestamp.from(range.fromInstant());
        Timestamp end = Timestamp.from(range.toInstant());

        Map<LocalDate, long[]> data = new HashMap<>();
        // [0] searches [1] suggestions [2] requests [3] accepted [4] rejected [5] expired [6] cancelled
        // [7] connections [8] teamRuns [9] teamOptions
        collect(data, 0, """
                SELECT %s AS period, count(*) AS total FROM matching_runs r JOIN users u ON u.id = r.user_id
                WHERE r.run_type = 'partner_search' AND r.status = 'success' AND r.created_at >= ? AND r.created_at < ?
                  AND u.email NOT LIKE '%%@bwf.example' GROUP BY period
                """.formatted(granularity.bucket("r.created_at")), start, end);
        collect(data, 1, """
                SELECT %s AS period, count(*) AS total FROM partner_suggestions s
                JOIN users u ON u.id = s.user_id JOIN users v ON v.id = s.suggested_user_id
                WHERE s.created_at >= ? AND s.created_at < ?
                  AND u.email NOT LIKE '%%@bwf.example' AND v.email NOT LIKE '%%@bwf.example' GROUP BY period
                """.formatted(granularity.bucket("s.created_at")), start, end);
        String requestSql = """
                SELECT %s AS period, count(*) AS total FROM partner_requests q
                JOIN users u ON u.id = q.requester_id JOIN users v ON v.id = q.receiver_id
                WHERE q.created_at >= ? AND q.created_at < ? %s
                  AND u.email NOT LIKE '%%@bwf.example' AND v.email NOT LIKE '%%@bwf.example' GROUP BY period
                """;
        collect(data, 2, requestSql.formatted(granularity.bucket("q.created_at"), ""), start, end);
        collect(data, 3, requestSql.formatted(granularity.bucket("q.created_at"), "AND q.status = 'accepted'"), start, end);
        collect(data, 4, requestSql.formatted(granularity.bucket("q.created_at"), "AND q.status = 'rejected'"), start, end);
        collect(data, 5, requestSql.formatted(granularity.bucket("q.created_at"), "AND q.status = 'expired'"), start, end);
        collect(data, 6, requestSql.formatted(granularity.bucket("q.created_at"), "AND q.status = 'cancelled'"), start, end);
        collect(data, 7, """
                SELECT %s AS period, count(*) AS total FROM player_connections c
                JOIN users u ON u.id = c.user_id JOIN users v ON v.id = c.partner_id
                WHERE c.user_id < c.partner_id AND c.created_at >= ? AND c.created_at < ?
                  AND u.email NOT LIKE '%%@bwf.example' AND v.email NOT LIKE '%%@bwf.example' GROUP BY period
                """.formatted(granularity.bucket("c.created_at")), start, end);
        collect(data, 8, """
                SELECT %s AS period, count(*) AS total FROM matching_runs r JOIN users u ON u.id = r.user_id
                WHERE r.run_type = 'double_team' AND r.status = 'success' AND r.created_at >= ? AND r.created_at < ?
                  AND u.email NOT LIKE '%%@bwf.example' GROUP BY period
                """.formatted(granularity.bucket("r.created_at")), start, end);
        collect(data, 9, """
                SELECT %s AS period, count(*) AS total FROM double_team_suggestions d JOIN users u ON u.id = d.requested_by
                WHERE d.batch_id IS NOT NULL AND d.created_at >= ? AND d.created_at < ?
                  AND u.email NOT LIKE '%%@bwf.example' GROUP BY period
                """.formatted(granularity.bucket("d.created_at")), start, end);

        List<Point> points = new ArrayList<>();
        long[] sum = new long[10];
        for (LocalDate period = granularity.start(range.from()); !period.isAfter(range.to()); period = granularity.next(period)) {
            long[] value = data.getOrDefault(period, new long[10]);
            for (int i = 0; i < sum.length; i++) sum[i] += value[i];
            points.add(point(period, value));
        }
        Double[] response = new Double[1];
        long[] samples = new long[1];
        jdbc.query("""
                SELECT avg(extract(epoch FROM (q.responded_at - q.created_at)) / 60.0) AS minutes, count(*) AS total
                FROM partner_requests q JOIN users u ON u.id = q.requester_id JOIN users v ON v.id = q.receiver_id
                WHERE q.status IN ('accepted', 'rejected') AND q.responded_at IS NOT NULL
                  AND q.created_at >= ? AND q.created_at < ?
                  AND u.email NOT LIKE '%@bwf.example' AND v.email NOT LIKE '%@bwf.example'
                """, rs -> {
            BigDecimal minutes = rs.getBigDecimal("minutes");
            response[0] = minutes == null ? null : minutes.setScale(1, RoundingMode.HALF_UP).doubleValue();
            samples[0] = rs.getLong("total");
        }, start, end);
        return new AdminMatchingStatsResponse(range.from(), range.to(), granularity.id, point(null, sum), response[0],
                samples[0], points);
    }

    private void collect(Map<LocalDate, long[]> data, int index, String sql, Timestamp start, Timestamp end) {
        jdbc.query(sql, rs -> {
            data.computeIfAbsent(rs.getDate("period").toLocalDate(), key -> new long[10])[index] = rs.getLong("total");
        }, start, end);
    }

    private static Point point(LocalDate period, long[] v) {
        return new Point(period, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9]);
    }
}
