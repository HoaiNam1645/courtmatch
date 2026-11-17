package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminPartnerHistoryResponse.Detail;
import com.courtly.api.admin.dto.AdminPartnerHistoryResponse.Item;
import com.courtly.api.admin.dto.AdminPartnerHistoryResponse.PlayerRef;
import com.courtly.api.admin.dto.AdminPartnerHistoryResponse.Search;
import com.courtly.common.PageResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
 * 2.3.75 - lich su ghep cap cua nguoi choi cho quan tri vien. Moi yeu cau duoc gan cho goi y moi nhat cua cung
 * cap trong {@value AdminPartnerAccuracyService#ATTRIBUTION_DAYS} ngay truoc do (cung cach gan voi 2.3.70).
 * Du lieu mau (@bwf.example) khong duoc tinh.
 */
@Service
@RequiredArgsConstructor
public class AdminPartnerHistoryService {

    static final String PERMISSION = "matching.view";
    private static final Set<String> OUTCOMES = Set.of("none", "pending", "accepted", "rejected", "expired", "cancelled");

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private static final String BASE = """
            FROM partner_suggestions ps
            JOIN users u ON u.id = ps.user_id
            JOIN users v ON v.id = ps.suggested_user_id
            LEFT JOIN ai_models m ON m.id = ps.model_id
            LEFT JOIN matching_runs run ON run.id = ps.search_id
            LEFT JOIN LATERAL (
                SELECT r.id, r.status, r.created_at, r.responded_at
                FROM partner_requests r
                WHERE r.requester_id = ps.user_id AND r.receiver_id = ps.suggested_user_id
                  AND r.created_at >= ps.created_at
                  AND r.created_at < ps.created_at + make_interval(days => ?)
                  AND NOT EXISTS (
                      SELECT 1 FROM partner_suggestions later
                      WHERE later.user_id = ps.user_id AND later.suggested_user_id = ps.suggested_user_id
                        AND later.created_at > ps.created_at AND later.created_at <= r.created_at)
                ORDER BY r.created_at
                LIMIT 1
            ) req ON true
            WHERE u.email NOT LIKE '%@bwf.example' AND v.email NOT LIKE '%@bwf.example'
            """;

    private static final String COLUMNS = """
            SELECT ps.id, ps.created_at, ps.rank_no, ps.total_score, ps.scoring_source, m.model_version,
                   u.id AS viewer_id, u.full_name AS viewer_name, u.email AS viewer_email,
                   v.id AS suggested_id, v.full_name AS suggested_name, v.email AS suggested_email,
                   req.id AS request_id, req.status AS request_status, req.created_at AS requested_at,
                   req.responded_at
            """;

    @Transactional(readOnly = true)
    public PageResponse<Item> list(UUID adminId, String q, String outcome, String source, LocalDate from, LocalDate to,
                                   int page, int size) {
        accessService.requirePermission(adminId, PERMISSION);
        MatchingRange range = MatchingRange.of(from, to);
        String scoringSource = MatchingRange.source(source);
        String status = outcome == null || outcome.isBlank() || "all".equals(outcome) ? null : outcome;
        if (status != null && !OUTCOMES.contains(status)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ket qua khong hop le.",
                    Map.of("outcome", "Chi nhan none, pending, accepted, rejected, expired hoac cancelled"));
        }

        StringBuilder where = new StringBuilder(" AND ps.created_at >= ? AND ps.created_at < ?");
        List<Object> args = new ArrayList<>();
        args.add(AdminPartnerAccuracyService.ATTRIBUTION_DAYS);
        args.add(Timestamp.from(range.fromInstant()));
        args.add(Timestamp.from(range.toInstant()));
        if (scoringSource != null) {
            where.append(" AND ps.scoring_source = ?");
            args.add(scoringSource);
        }
        if (q != null && !q.isBlank()) {
            where.append("""
                     AND (strpos(lower(u.full_name), lower(?)) > 0 OR strpos(lower(u.email), lower(?)) > 0
                          OR strpos(lower(v.full_name), lower(?)) > 0 OR strpos(lower(v.email), lower(?)) > 0)
                    """);
            String term = q.trim();
            for (int i = 0; i < 4; i++) args.add(term);
        }
        if ("none".equals(status)) {
            where.append(" AND req.id IS NULL");
        } else if (status != null) {
            where.append(" AND req.status = ?");
            args.add(status);
        }

        Long total = jdbc.queryForObject("SELECT count(*) " + BASE + where, Long.class, args.toArray());
        long totalElements = total == null ? 0 : total;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<Item> items = jdbc.query(COLUMNS + BASE + where + " ORDER BY ps.created_at DESC, ps.id LIMIT ? OFFSET ?",
                (rs, row) -> item(rs), pageArgs.toArray());
        return new PageResponse<>(items, page, size, totalElements, (int) Math.ceil(totalElements / (double) size));
    }

    @Transactional(readOnly = true)
    public Detail get(UUID adminId, UUID suggestionId) {
        accessService.requirePermission(adminId, PERMISSION);
        List<Detail> found = jdbc.query(COLUMNS + """
                , ps.skill_score, ps.location_score, ps.time_score, ps.style_score, ps.confidence_score,
                  ps.reason_json::text AS reason, ps.search_id, run.duration_ms, run.result_count,
                  run.params_json::text AS run_params
                """ + BASE + " AND ps.id = ?", (rs, row) -> {
            UUID searchId = rs.getObject("search_id", UUID.class);
            Integer duration = (Integer) rs.getObject("duration_ms");
            return new Detail(item(rs), rs.getBigDecimal("skill_score"), rs.getBigDecimal("location_score"),
                    rs.getBigDecimal("time_score"), rs.getBigDecimal("style_score"), rs.getBigDecimal("confidence_score"),
                    json(rs.getString("reason")),
                    searchId == null ? null : new Search(searchId, duration, (Integer) rs.getObject("result_count"),
                            json(rs.getString("run_params"))));
        }, AdminPartnerAccuracyService.ATTRIBUTION_DAYS, suggestionId);
        if (found.isEmpty()) {
            throw ApiException.notFound("Khong tim thay goi y ghep cap.");
        }
        return found.getFirst();
    }

    private static Item item(ResultSet rs) throws SQLException {
        String status = rs.getString("request_status");
        return new Item(rs.getObject("id", UUID.class), instant(rs.getTimestamp("created_at")),
                new PlayerRef(rs.getObject("viewer_id", UUID.class), rs.getString("viewer_name"), rs.getString("viewer_email")),
                new PlayerRef(rs.getObject("suggested_id", UUID.class), rs.getString("suggested_name"), rs.getString("suggested_email")),
                (Integer) rs.getObject("rank_no"), rs.getBigDecimal("total_score"), rs.getString("scoring_source"),
                rs.getString("model_version"), status == null ? "none" : status,
                rs.getObject("request_id", UUID.class), instant(rs.getTimestamp("requested_at")),
                instant(rs.getTimestamp("responded_at")));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private Map<String, Object> json(String text) {
        if (text == null) return null;
        try {
            return objectMapper.readValue(text, new TypeReference<>() { });
        } catch (Exception e) {
            return null;
        }
    }
}
