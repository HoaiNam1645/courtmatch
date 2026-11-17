package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminPartnerAccuracyResponse;
import com.courtly.api.admin.dto.AdminPartnerAccuracyResponse.Group;
import com.courtly.api.admin.dto.AdminPartnerAccuracyResponse.Metrics;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.70 - do do chinh xac ghep cap nguoi choi.
 *
 * <p>Moi yeu cau ghep cap duoc gan cho goi y MOI NHAT cua cung cap (nguoi xem, nguoi duoc goi y)
 * da xuat hien truoc yeu cau, trong {@value #ATTRIBUTION_DAYS} ngay. Du lieu mau (@bwf.example) khong duoc tinh.
 */
@Service
@RequiredArgsConstructor
public class AdminPartnerAccuracyService {

    static final String PERMISSION = "matching.view";
    static final int MIN_SAMPLE = 30;
    static final int ATTRIBUTION_DAYS = 7;

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;

    /** Mot goi y cung ket qua yeu cau gan voi no (neu co). */
    record Outcome(int rank, boolean ranked, double score, String requestStatus, boolean played) {
    }

    @Transactional(readOnly = true)
    public AdminPartnerAccuracyResponse get(UUID adminId, LocalDate from, LocalDate to, String source) {
        accessService.requirePermission(adminId, PERMISSION);
        MatchingRange range = MatchingRange.of(from, to);
        String scoringSource = MatchingRange.source(source);
        List<Outcome> outcomes = load(range, scoringSource);

        List<Group> byRank = List.of(
                rankGroup(outcomes, 1, "Top 1"), rankGroup(outcomes, 3, "Top 3"), rankGroup(outcomes, 10, "Top 10"));
        List<Group> byScore = List.of(
                scoreGroup(outcomes, 0, 40, "0-40"), scoreGroup(outcomes, 40, 70, "40-70"),
                scoreGroup(outcomes, 70, 100.01, "70-100"));
        return new AdminPartnerAccuracyResponse(range.from(), range.to(), scoringSource, MIN_SAMPLE, ATTRIBUTION_DAYS,
                metrics(outcomes), byRank, byScore);
    }

    private List<Outcome> load(MatchingRange range, String source) {
        String sql = """
                WITH s AS (
                    SELECT ps.id, ps.user_id, ps.suggested_user_id, ps.total_score, ps.rank_no, ps.created_at
                    FROM partner_suggestions ps
                    JOIN users u ON u.id = ps.user_id
                    JOIN users v ON v.id = ps.suggested_user_id
                    WHERE ps.created_at >= ? AND ps.created_at < ?
                      AND (?::text IS NULL OR ps.scoring_source = ?)
                      AND u.email NOT LIKE '%@bwf.example' AND v.email NOT LIKE '%@bwf.example'
                ),
                attr AS (
                    SELECT DISTINCT ON (s.id) s.id AS sid, r.status, r.responded_at
                    FROM s
                    JOIN partner_requests r ON r.requester_id = s.user_id AND r.receiver_id = s.suggested_user_id
                     AND r.created_at >= s.created_at
                     AND r.created_at < s.created_at + make_interval(days => ?)
                    WHERE NOT EXISTS (
                        SELECT 1 FROM partner_suggestions later
                        WHERE later.user_id = s.user_id AND later.suggested_user_id = s.suggested_user_id
                          AND later.created_at > s.created_at AND later.created_at <= r.created_at)
                    ORDER BY s.id, r.created_at
                )
                SELECT s.total_score, s.rank_no, attr.status,
                       (attr.status = 'accepted' AND EXISTS (
                            SELECT 1 FROM match_players a
                            JOIN match_players b ON b.match_id = a.match_id
                            JOIN matches m ON m.id = a.match_id
                            WHERE a.user_id = s.user_id AND b.user_id = s.suggested_user_id
                              AND m.status = 'completed'
                              AND COALESCE(m.scheduled_at, m.created_at) >= attr.responded_at)) AS played
                FROM s LEFT JOIN attr ON attr.sid = s.id
                """;
        Timestamp start = Timestamp.from(range.fromInstant());
        Timestamp end = Timestamp.from(range.toInstant());
        return jdbc.query(sql, (rs, row) -> {
            int rank = rs.getInt("rank_no");
            boolean ranked = !rs.wasNull();
            return new Outcome(rank, ranked, rs.getBigDecimal("total_score").doubleValue(),
                    rs.getString("status"), rs.getBoolean("played"));
        }, start, end, source, source, ATTRIBUTION_DAYS);
    }

    private static Group rankGroup(List<Outcome> outcomes, int maxRank, String label) {
        return new Group("top" + maxRank, label, metrics(filter(outcomes, o -> o.ranked() && o.rank() <= maxRank)));
    }

    private static Group scoreGroup(List<Outcome> outcomes, double min, double max, String label) {
        return new Group(label, label, metrics(filter(outcomes, o -> o.score() >= min && o.score() < max)));
    }

    private static List<Outcome> filter(List<Outcome> outcomes, Predicate<Outcome> predicate) {
        return outcomes.stream().filter(predicate).toList();
    }

    static Metrics metrics(List<Outcome> outcomes) {
        long total = outcomes.size();
        long accepted = count(outcomes, "accepted");
        long rejected = count(outcomes, "rejected");
        long expired = count(outcomes, "expired");
        long cancelled = count(outcomes, "cancelled");
        long pending = count(outcomes, "pending");
        long requested = accepted + rejected + expired + cancelled + pending;
        long answered = accepted + rejected;
        long played = outcomes.stream().filter(Outcome::played).count();
        return new Metrics(total, requested, percent(requested, total), accepted, rejected, expired, cancelled,
                pending, answered, percent(accepted, answered), played, percent(played, accepted),
                total >= MIN_SAMPLE);
    }

    private static long count(List<Outcome> outcomes, String status) {
        return outcomes.stream().filter(o -> status.equals(o.requestStatus())).count();
    }

    /** Phan tram lam tron 1 chu so; null khi mau so bang 0 (khong co gi de do). */
    static Double percent(long part, long whole) {
        if (whole == 0) return null;
        return BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
