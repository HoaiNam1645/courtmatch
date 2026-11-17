package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminTeamAccuracyResponse;
import com.courtly.api.admin.dto.AdminTeamAccuracyResponse.Metrics;
import com.courtly.api.admin.dto.AdminTeamAccuracyResponse.Sample;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.71 - do do chinh xac ghep doi.
 *
 * <p>De xuat chia doi khong tu tao tran, nen chi doi chieu khi 4 nguoi do ve sau co mot tran da xac nhan ket qua
 * voi dung cach chia doi cua mot phuong an (trong {@value #RECONCILE_DAYS} ngay). Du lieu mau (@bwf.example)
 * khong duoc tinh.
 */
@Service
@RequiredArgsConstructor
public class AdminTeamAccuracyService {

    static final String PERMISSION = "matching.view";
    static final int MIN_SAMPLE = 30;
    static final int RECONCILE_DAYS = 30;
    static final int SAMPLE_LIMIT = 10;

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;

    /** Mot phuong an da doi chieu voi tran that. */
    record Reconciled(UUID batchId, UUID matchId, int rank, boolean recommended, double team1Probability,
                      double balanceGap, int winningTeam, Double pointMargin, java.time.Instant suggestedAt,
                      java.time.Instant playedAt) {
        int predictedWinner() {
            return team1Probability > 50.0 ? 1 : team1Probability < 50.0 ? 2 : 0;
        }

        /** Doi 1 cua phuong an co thang khong. */
        boolean team1Won() {
            return winningTeam == 1;
        }
    }

    @Transactional(readOnly = true)
    public AdminTeamAccuracyResponse get(UUID adminId, LocalDate from, LocalDate to, String source) {
        accessService.requirePermission(adminId, PERMISSION);
        MatchingRange range = MatchingRange.of(from, to);
        String scoringSource = MatchingRange.source(source);
        Timestamp start = Timestamp.from(range.fromInstant());
        Timestamp end = Timestamp.from(range.toInstant());

        long batches = count("""
                SELECT count(DISTINCT d.batch_id) FROM double_team_suggestions d JOIN users u ON u.id = d.requested_by
                WHERE d.batch_id IS NOT NULL AND d.created_at >= ? AND d.created_at < ?
                  AND (?::text IS NULL OR d.scoring_source = ?) AND u.email NOT LIKE '%@bwf.example'
                """, start, end, scoringSource, scoringSource);
        long options = count("""
                SELECT count(*) FROM double_team_suggestions d JOIN users u ON u.id = d.requested_by
                WHERE d.batch_id IS NOT NULL AND d.created_at >= ? AND d.created_at < ?
                  AND (?::text IS NULL OR d.scoring_source = ?) AND u.email NOT LIKE '%@bwf.example'
                """, start, end, scoringSource, scoringSource);

        List<Reconciled> all = load(start, end, scoringSource);
        List<Reconciled> recommended = all.stream().filter(Reconciled::recommended).toList();
        List<Reconciled> others = all.stream().filter(item -> !item.recommended()).toList();
        long reconciledBatches = all.stream().map(Reconciled::batchId).distinct().count();
        long chosenBatches = recommended.stream().map(Reconciled::batchId).distinct().count();

        List<Sample> samples = all.stream()
                .sorted((x, y) -> y.playedAt().compareTo(x.playedAt()))
                .limit(SAMPLE_LIMIT)
                .map(AdminTeamAccuracyService::sample)
                .toList();
        return new AdminTeamAccuracyResponse(range.from(), range.to(), scoringSource, MIN_SAMPLE, RECONCILE_DAYS,
                batches, options, metrics(all), metrics(recommended), metrics(others),
                AdminPartnerAccuracyService.percent(chosenBatches, reconciledBatches), samples);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private List<Reconciled> load(Timestamp start, Timestamp end, String source) {
        String sql = """
                WITH opt AS (
                    SELECT d.id, d.batch_id, d.rank_no, d.recommended, d.created_at, d.balance_score,
                           COALESCE((d.detail_json -> 'option' ->> 'team1WinProbability')::numeric,
                                    100.0 / (1 + power(10, (d.team2_strength - d.team1_strength) / 400.0))) AS p1,
                           (SELECT array_agg(x::uuid ORDER BY x::uuid) FROM jsonb_array_elements_text(d.team1_players) x) AS t1,
                           (SELECT array_agg(x::uuid ORDER BY x::uuid) FROM jsonb_array_elements_text(d.team2_players) x) AS t2
                    FROM double_team_suggestions d JOIN users u ON u.id = d.requested_by
                    WHERE d.batch_id IS NOT NULL AND d.created_at >= ? AND d.created_at < ?
                      AND (?::text IS NULL OR d.scoring_source = ?) AND u.email NOT LIKE '%@bwf.example'
                ),
                played AS (
                    SELECT m.id, COALESCE(m.scheduled_at, m.created_at) AS played_at, r.winning_team,
                           array_agg(mp.user_id ORDER BY mp.user_id) FILTER (WHERE mp.team_no = 1) AS t1,
                           array_agg(mp.user_id ORDER BY mp.user_id) FILTER (WHERE mp.team_no = 2) AS t2,
                           (SELECT sum(abs(g.team1_score - g.team2_score)) FROM match_games g WHERE g.match_id = m.id) AS margin
                    FROM matches m
                    JOIN match_results r ON r.match_id = m.id
                    JOIN match_players mp ON mp.match_id = m.id
                    WHERE m.status = 'completed'
                    GROUP BY m.id, r.winning_team
                    HAVING count(*) = 4
                )
                SELECT DISTINCT ON (opt.id) opt.batch_id, p.id AS match_id, opt.rank_no, opt.recommended, opt.p1,
                       opt.balance_score, opt.created_at, p.played_at, p.margin,
                       CASE WHEN p.t1 = opt.t1 THEN p.winning_team ELSE 3 - p.winning_team END AS option_winner
                FROM opt JOIN played p
                  ON ((p.t1 = opt.t1 AND p.t2 = opt.t2) OR (p.t1 = opt.t2 AND p.t2 = opt.t1))
                 AND p.played_at >= opt.created_at
                 AND p.played_at < opt.created_at + make_interval(days => ?)
                ORDER BY opt.id, p.played_at
                """;
        return jdbc.query(sql, (rs, row) -> {
            BigDecimal margin = rs.getBigDecimal("margin");
            return new Reconciled(rs.getObject("batch_id", UUID.class), rs.getObject("match_id", UUID.class),
                    rs.getInt("rank_no"), rs.getBoolean("recommended"), rs.getBigDecimal("p1").doubleValue(),
                    rs.getBigDecimal("balance_score").doubleValue(), rs.getInt("option_winner"),
                    margin == null ? null : margin.doubleValue(),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("played_at").toInstant());
        }, start, end, source, source, RECONCILE_DAYS);
    }

    private static Sample sample(Reconciled item) {
        int predicted = item.predictedWinner();
        return new Sample(item.batchId(), item.matchId(), item.rank(), item.recommended(),
                item.team1Probability(), item.winningTeam(), predicted, predicted != 0 && predicted == item.winningTeam(),
                item.balanceGap(), item.suggestedAt(), item.playedAt());
    }

    static Metrics metrics(List<Reconciled> items) {
        long reconciled = items.size();
        List<Reconciled> evaluated = items.stream().filter(item -> item.predictedWinner() != 0).toList();
        long correct = evaluated.stream().filter(item -> item.predictedWinner() == item.winningTeam()).count();
        Double brier = reconciled == 0 ? null : round(items.stream()
                .mapToDouble(item -> Math.pow(item.team1Probability() / 100.0 - (item.team1Won() ? 1.0 : 0.0), 2))
                .average().orElse(0), 3);
        Double gap = reconciled == 0 ? null : round(items.stream().mapToDouble(Reconciled::balanceGap).average().orElse(0), 1);
        double marginSum = 0;
        int marginCount = 0;
        for (Reconciled item : items) {
            if (item.pointMargin() != null) {
                marginSum += item.pointMargin();
                marginCount++;
            }
        }
        Double margin = marginCount == 0 ? null : round(marginSum / marginCount, 1);
        return new Metrics(reconciled, evaluated.size(), correct,
                AdminPartnerAccuracyService.percent(correct, evaluated.size()), brier, gap, margin,
                reconciled >= MIN_SAMPLE);
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
