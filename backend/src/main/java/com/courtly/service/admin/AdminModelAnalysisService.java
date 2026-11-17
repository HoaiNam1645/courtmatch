package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminModelAnalysisResponse;
import com.courtly.api.admin.dto.AdminModelAnalysisResponse.Bucket;
import com.courtly.api.admin.dto.AdminModelAnalysisResponse.Model;
import com.courtly.api.admin.dto.AdminModelAnalysisResponse.Partner;
import com.courtly.api.admin.dto.AdminModelAnalysisResponse.Team;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
 * 2.3.72 - phan tich ket qua cham diem cua tung model: so lan cham, phan bo diem, diem thanh phan trung binh,
 * ty le thieu du lieu va ty le chap nhan (dung cach gan yeu cau cua 2.3.70). Du lieu mau khong duoc tinh.
 */
@Service
@RequiredArgsConstructor
public class AdminModelAnalysisService {

    static final String PERMISSION = "matching.view";
    static final int MIN_SAMPLE = AdminPartnerAccuracyService.MIN_SAMPLE;
    private static final List<String> BUCKET_LABELS = List.of("0-20", "20-40", "40-60", "60-80", "80-100");

    private final AdminAccessService accessService;
    private final AdminPartnerAccuracyService partnerAccuracyService;
    private final JdbcTemplate jdbc;

    private record ModelRow(UUID id, String type, String version, String status, Instant trainedAt, Instant activatedAt) {
    }

    @Transactional(readOnly = true)
    public AdminModelAnalysisResponse get(UUID adminId, LocalDate from, LocalDate to, String source) {
        accessService.requirePermission(adminId, PERMISSION);
        MatchingRange range = MatchingRange.of(from, to);
        String scoringSource = MatchingRange.source(source);
        Timestamp start = Timestamp.from(range.fromInstant());
        Timestamp end = Timestamp.from(range.toInstant());

        Map<UUID, List<AdminPartnerAccuracyService.Outcome>> outcomes = new HashMap<>();
        for (AdminPartnerAccuracyService.Outcome outcome : partnerAccuracyService.load(range, scoringSource)) {
            if (outcome.modelId() != null) outcomes.computeIfAbsent(outcome.modelId(), key -> new ArrayList<>()).add(outcome);
        }
        Map<UUID, Partner> partners = partnerStats(start, end, scoringSource, outcomes);
        Map<UUID, Team> teams = teamStats(start, end, scoringSource);

        List<Model> models = new ArrayList<>();
        for (ModelRow row : models(partners.keySet(), teams.keySet())) {
            models.add(new Model(row.id(), row.type(), row.version(), row.status(), row.trainedAt(), row.activatedAt(),
                    partners.get(row.id()), teams.get(row.id())));
        }
        return new AdminModelAnalysisResponse(range.from(), range.to(), scoringSource, MIN_SAMPLE, models);
    }

    /** Model co ket qua trong khoang, cong voi model dang hoat dong de luon thay duoc model hien hanh. */
    private List<ModelRow> models(java.util.Set<UUID> partnerIds, java.util.Set<UUID> teamIds) {
        java.util.Set<UUID> used = new java.util.HashSet<>(partnerIds);
        used.addAll(teamIds);
        return jdbc.query("SELECT id, model_type, model_version, status, trained_at, activated_at FROM ai_models ORDER BY model_type, model_version",
                (rs, row) -> new ModelRow(rs.getObject("id", UUID.class), rs.getString("model_type"),
                        rs.getString("model_version"), rs.getString("status"),
                        instant(rs.getTimestamp("trained_at")), instant(rs.getTimestamp("activated_at"))))
                .stream().filter(row -> used.contains(row.id()) || "active".equals(row.status())).toList();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private Map<UUID, Partner> partnerStats(Timestamp start, Timestamp end, String source,
                                            Map<UUID, List<AdminPartnerAccuracyService.Outcome>> outcomes) {
        Map<UUID, Partner> result = new HashMap<>();
        jdbc.query("""
                SELECT ps.model_id, count(*) AS n,
                       avg(ps.total_score) AS total, avg(ps.skill_score) AS skill, avg(ps.location_score) AS location,
                       avg(ps.time_score) AS time, avg(ps.style_score) AS style,
                       count(*) FILTER (WHERE ps.time_score IS NULL) AS no_time,
                       count(*) FILTER (WHERE ps.location_score IS NULL) AS no_location,
                       count(*) FILTER (WHERE ps.total_score < 20) AS b1,
                       count(*) FILTER (WHERE ps.total_score >= 20 AND ps.total_score < 40) AS b2,
                       count(*) FILTER (WHERE ps.total_score >= 40 AND ps.total_score < 60) AS b3,
                       count(*) FILTER (WHERE ps.total_score >= 60 AND ps.total_score < 80) AS b4,
                       count(*) FILTER (WHERE ps.total_score >= 80) AS b5
                FROM partner_suggestions ps
                JOIN users u ON u.id = ps.user_id
                JOIN users v ON v.id = ps.suggested_user_id
                WHERE ps.model_id IS NOT NULL AND ps.created_at >= ? AND ps.created_at < ?
                  AND (?::text IS NULL OR ps.scoring_source = ?)
                  AND u.email NOT LIKE '%@bwf.example' AND v.email NOT LIKE '%@bwf.example'
                GROUP BY ps.model_id
                """, rs -> {
            UUID id = rs.getObject("model_id", UUID.class);
            long scored = rs.getLong("n");
            List<Bucket> distribution = new ArrayList<>();
            for (int i = 0; i < BUCKET_LABELS.size(); i++) {
                distribution.add(new Bucket(BUCKET_LABELS.get(i), rs.getLong("b" + (i + 1))));
            }
            var metrics = AdminPartnerAccuracyService.metrics(outcomes.getOrDefault(id, List.of()));
            result.put(id, new Partner(scored, avg(rs.getBigDecimal("total")), avg(rs.getBigDecimal("skill")),
                    avg(rs.getBigDecimal("location")), avg(rs.getBigDecimal("time")), avg(rs.getBigDecimal("style")),
                    AdminPartnerAccuracyService.percent(rs.getLong("no_time"), scored),
                    AdminPartnerAccuracyService.percent(rs.getLong("no_location"), scored),
                    distribution, metrics.requested(), metrics.answered(), metrics.accepted(),
                    metrics.acceptanceRate(), scored >= MIN_SAMPLE));
        }, start, end, source, source);
        return result;
    }

    /** Moi lan chia doi chi tinh mot lan (phuong an khuyen nghi); thieu du lieu tinh tren cac cap cua lan do. */
    private Map<UUID, Team> teamStats(Timestamp start, Timestamp end, String source) {
        Map<UUID, Team> result = new HashMap<>();
        jdbc.query("""
                SELECT d.model_id, count(DISTINCT d.id) AS batches, avg(d.balance_score) AS gap,
                       count(pair) AS pairs,
                       count(pair) FILTER (WHERE pair ->> 'timeScore' IS NULL) AS no_time,
                       count(pair) FILTER (WHERE pair ->> 'locationScore' IS NULL) AS no_location
                FROM double_team_suggestions d
                JOIN users u ON u.id = d.requested_by
                LEFT JOIN LATERAL jsonb_array_elements(d.detail_json -> 'pairs') pair ON true
                WHERE d.recommended AND d.batch_id IS NOT NULL AND d.created_at >= ? AND d.created_at < ?
                  AND (?::text IS NULL OR d.scoring_source = ?) AND u.email NOT LIKE '%@bwf.example'
                GROUP BY d.model_id
                """, rs -> {
            long batches = rs.getLong("batches");
            long pairs = rs.getLong("pairs");
            result.put(rs.getObject("model_id", UUID.class), new Team(batches, avg(rs.getBigDecimal("gap")), pairs,
                    AdminPartnerAccuracyService.percent(rs.getLong("no_time"), pairs),
                    AdminPartnerAccuracyService.percent(rs.getLong("no_location"), pairs), batches >= MIN_SAMPLE));
        }, start, end, source, source);
        return result;
    }

    private static Double avg(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
