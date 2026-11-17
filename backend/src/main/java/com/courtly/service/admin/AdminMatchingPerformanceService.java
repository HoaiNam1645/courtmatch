package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminMatchingPerformanceResponse;
import com.courtly.api.admin.dto.AdminMatchingPerformanceResponse.Alert;
import com.courtly.api.admin.dto.AdminMatchingPerformanceResponse.Day;
import com.courtly.api.admin.dto.AdminMatchingPerformanceResponse.ModelQuality;
import com.courtly.api.admin.dto.AdminMatchingPerformanceResponse.Summary;
import com.courtly.api.admin.dto.AdminMatchingPerformanceResponse.Thresholds;
import com.courtly.service.matching.MatchingRunRecorder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.73 - hieu suat AI service va chat luong model. Do tren nhat ky matching_runs (thoi gian xu ly, loi) va
 * ket qua that cua goi y (2.3.70); canh bao khi vuot nguong cau hinh. Chi canh bao ty le khi du mau.
 */
@Service
public class AdminMatchingPerformanceService {

    static final String PERMISSION = "matching.view";
    /** So lan chay toi thieu de ty le loi / thoi gian co y nghia. */
    static final int MIN_RUNS = 20;

    private final AdminAccessService accessService;
    private final AdminPartnerAccuracyService partnerAccuracyService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Thresholds thresholds;

    public AdminMatchingPerformanceService(AdminAccessService accessService,
                                           AdminPartnerAccuracyService partnerAccuracyService, JdbcTemplate jdbc,
                                           ObjectMapper objectMapper,
                                           @Value("${courtly.matching.quality.min-acceptance-rate:20.0}") double minAcceptanceRate,
                                           @Value("${courtly.matching.quality.max-failure-rate:5.0}") double maxFailureRate,
                                           @Value("${courtly.matching.quality.max-model-age-days:90}") int maxModelAgeDays,
                                           @Value("${courtly.matching.quality.max-p95-ms:1500}") int maxP95Ms) {
        this.accessService = accessService;
        this.partnerAccuracyService = partnerAccuracyService;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.thresholds = new Thresholds(minAcceptanceRate, maxFailureRate, maxModelAgeDays, maxP95Ms);
    }

    @Transactional(readOnly = true)
    public AdminMatchingPerformanceResponse get(UUID adminId, LocalDate from, LocalDate to) {
        accessService.requirePermission(adminId, PERMISSION);
        MatchingRange range = MatchingRange.of(from, to);
        Timestamp start = Timestamp.from(range.fromInstant());
        Timestamp end = Timestamp.from(range.toInstant());

        Summary partner = summary(MatchingRunRecorder.PARTNER_SEARCH, start, end);
        Summary team = summary(MatchingRunRecorder.DOUBLE_TEAM, start, end);
        List<ModelQuality> models = models();
        boolean aiActive = models.stream().anyMatch(item -> "catboost_ranker".equals(item.type()) && "active".equals(item.status()));

        List<Alert> alerts = alerts(range, partner, team, models, aiActive);
        return new AdminMatchingPerformanceResponse(range.from(), range.to(), aiActive, thresholds, partner, team,
                daily(range, start, end), models, alerts);
    }

    private Summary summary(String type, Timestamp start, Timestamp end) {
        return jdbc.queryForObject("""
                SELECT count(*) AS runs,
                       count(*) FILTER (WHERE r.status = 'failed') AS failed,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY r.duration_ms) AS p50,
                       percentile_cont(0.95) WITHIN GROUP (ORDER BY r.duration_ms) AS p95,
                       max(r.duration_ms) AS max_ms,
                       count(*) FILTER (WHERE r.scoring_source = 'baseline') AS baseline,
                       avg(r.result_count) FILTER (WHERE r.status = 'success') AS avg_results
                FROM matching_runs r JOIN users u ON u.id = r.user_id
                WHERE r.run_type = ? AND r.created_at >= ? AND r.created_at < ? AND u.email NOT LIKE '%@bwf.example'
                """, (rs, row) -> {
            long runs = rs.getLong("runs");
            long failed = rs.getLong("failed");
            long baseline = rs.getLong("baseline");
            return new Summary(runs, failed, AdminPartnerAccuracyService.percent(failed, runs),
                    runs == 0 ? null : Math.round(rs.getDouble("p50")), runs == 0 ? null : Math.round(rs.getDouble("p95")),
                    runs == 0 ? null : rs.getLong("max_ms"), baseline,
                    AdminPartnerAccuracyService.percent(baseline, runs),
                    rs.getBigDecimal("avg_results") == null ? null
                            : rs.getBigDecimal("avg_results").setScale(1, java.math.RoundingMode.HALF_UP).doubleValue());
        }, type, start, end);
    }

    private List<Day> daily(MatchingRange range, Timestamp start, Timestamp end) {
        Map<LocalDate, long[]> counts = new HashMap<>();
        jdbc.query("""
                SELECT (r.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day,
                       count(*) FILTER (WHERE r.run_type = 'partner_search') AS partner,
                       count(*) FILTER (WHERE r.run_type = 'double_team') AS team,
                       count(*) FILTER (WHERE r.status = 'failed') AS failed
                FROM matching_runs r JOIN users u ON u.id = r.user_id
                WHERE r.created_at >= ? AND r.created_at < ? AND u.email NOT LIKE '%@bwf.example'
                GROUP BY day
                """, rs -> {
            counts.put(rs.getDate("day").toLocalDate(),
                    new long[] {rs.getLong("partner"), rs.getLong("team"), rs.getLong("failed")});
        }, start, end);
        List<Day> days = new ArrayList<>();
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            long[] value = counts.getOrDefault(day, new long[3]);
            days.add(new Day(day, value[0], value[1], value[2]));
        }
        return days;
    }

    private List<ModelQuality> models() {
        Instant now = Instant.now();
        return jdbc.query("""
                SELECT id, model_type, model_version, status, trained_at, activated_at, metrics_json::text AS metrics
                FROM ai_models WHERE status IN ('active', 'ready', 'training', 'failed')
                ORDER BY (status = 'active') DESC, model_type, model_version
                """, (rs, row) -> {
            Instant trained = rs.getTimestamp("trained_at") == null ? null : rs.getTimestamp("trained_at").toInstant();
            Instant activated = rs.getTimestamp("activated_at") == null ? null : rs.getTimestamp("activated_at").toInstant();
            Instant reference = activated != null ? activated : trained;
            return new ModelQuality(rs.getObject("id", UUID.class), rs.getString("model_type"),
                    rs.getString("model_version"), rs.getString("status"), trained, activated,
                    reference == null ? null : Duration.between(reference, now).toDays(), metrics(rs.getString("metrics")));
        });
    }

    private Map<String, Object> metrics(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception e) {
            return null;
        }
    }

    private List<Alert> alerts(MatchingRange range, Summary partner, Summary team, List<ModelQuality> models,
                               boolean aiActive) {
        List<Alert> alerts = new ArrayList<>();
        if (!aiActive) {
            alerts.add(new Alert("no_ai_service", "info",
                    "Chưa có AI service hay mô hình CatBoost đang hoạt động; mọi lượt chấm điểm đang dùng công thức baseline."));
        }
        failureAlert(alerts, "Tìm đối tác", partner);
        failureAlert(alerts, "Chia đội", team);
        slowAlert(alerts, "Tìm đối tác", partner);
        slowAlert(alerts, "Chia đội", team);

        var accuracy = AdminPartnerAccuracyService.metrics(partnerAccuracyService.load(range, null));
        if (accuracy.sufficient() && accuracy.answered() > 0 && accuracy.acceptanceRate() != null
                && accuracy.acceptanceRate() < thresholds.minAcceptanceRate()) {
            alerts.add(new Alert("low_acceptance", "warning", "Tỷ lệ chấp nhận yêu cầu ghép cặp chỉ %s%%, thấp hơn ngưỡng %s%% (%d gợi ý)."
                    .formatted(accuracy.acceptanceRate(), thresholds.minAcceptanceRate(), accuracy.suggestions())));
        }
        for (ModelQuality model : models) {
            if ("active".equals(model.status()) && model.ageDays() != null && model.ageDays() > thresholds.maxModelAgeDays()) {
                alerts.add(new Alert("model_stale", "warning", "Mô hình %s (%s) đã %d ngày chưa được huấn luyện lại, quá ngưỡng %d ngày."
                        .formatted(model.version(), model.type(), model.ageDays(), thresholds.maxModelAgeDays())));
            }
        }
        return alerts;
    }

    private void failureAlert(List<Alert> alerts, String label, Summary summary) {
        if (summary.runs() >= MIN_RUNS && summary.failureRate() != null && summary.failureRate() > thresholds.maxFailureRate()) {
            alerts.add(new Alert("high_failure_rate", "warning", "%s lỗi %s%% (%d/%d lượt), vượt ngưỡng %s%%."
                    .formatted(label, summary.failureRate(), summary.failed(), summary.runs(), thresholds.maxFailureRate())));
        }
    }

    private void slowAlert(List<Alert> alerts, String label, Summary summary) {
        if (summary.runs() >= MIN_RUNS && summary.p95Ms() != null && summary.p95Ms() > thresholds.maxP95Ms()) {
            alerts.add(new Alert("slow_response", "warning", "%s có p95 %d ms, vượt ngưỡng %d ms."
                    .formatted(label, summary.p95Ms(), thresholds.maxP95Ms())));
        }
    }
}
