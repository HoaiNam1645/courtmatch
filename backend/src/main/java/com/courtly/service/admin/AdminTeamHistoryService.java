package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminPartnerHistoryResponse.PlayerRef;
import com.courtly.api.admin.dto.AdminTeamHistoryResponse.Detail;
import com.courtly.api.admin.dto.AdminTeamHistoryResponse.Item;
import com.courtly.api.admin.dto.AdminTeamHistoryResponse.Player;
import com.courtly.api.admin.dto.AdminTeamHistoryResponse.Reconciliation;
import com.courtly.api.admin.dto.AdminTeamHistoryResponse.Recommended;
import com.courtly.api.doubleteam.dto.DoubleTeamSuggestionResponse;
import com.courtly.common.PageResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
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
 * 2.3.76 - lich su chia doi cho quan tri vien. Moi dong la mot lan chia (batch) kem phuong an khuyen nghi va ket qua
 * doi chieu voi tran that cung cach chia doi (cung quy tac voi 2.3.71). Du lieu mau (@bwf.example) khong duoc tinh.
 */
@Service
@RequiredArgsConstructor
public class AdminTeamHistoryService {

    static final String PERMISSION = "matching.view";
    private static final Set<String> TYPES = Set.of("mens", "womens", "mixed");
    private static final Set<String> PLAYED = Set.of("all", "played", "unplayed");

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    /** d = phuong an khuyen nghi cua batch; rec = tran that sam nhat khop mot phuong an bat ky cua batch. */
    private static final String BASE = """
            FROM double_team_suggestions d
            JOIN users u ON u.id = d.requested_by
            JOIN ai_models m ON m.id = d.model_id
            LEFT JOIN LATERAL (
                SELECT p.id AS match_id, p.played_at, o.rank_no, o.recommended,
                       CASE WHEN o.p1 = 50 THEN NULL
                            ELSE (CASE WHEN o.p1 > 50 THEN 1 ELSE 2 END)
                                 = (CASE WHEN p.t1 = o.t1 THEN p.winning_team ELSE 3 - p.winning_team END) END AS correct
                FROM (
                    SELECT x.rank_no, x.recommended, x.created_at,
                           COALESCE((x.detail_json -> 'option' ->> 'team1WinProbability')::numeric,
                                    100.0 / (1 + power(10, (x.team2_strength - x.team1_strength) / 400.0))) AS p1,
                           (SELECT array_agg(e::uuid ORDER BY e::uuid) FROM jsonb_array_elements_text(x.team1_players) e) AS t1,
                           (SELECT array_agg(e::uuid ORDER BY e::uuid) FROM jsonb_array_elements_text(x.team2_players) e) AS t2
                    FROM double_team_suggestions x WHERE x.batch_id = d.batch_id
                ) o
                JOIN LATERAL (
                    SELECT mt.id, COALESCE(mt.scheduled_at, mt.created_at) AS played_at, r.winning_team,
                           array_agg(mp.user_id ORDER BY mp.user_id) FILTER (WHERE mp.team_no = 1) AS t1,
                           array_agg(mp.user_id ORDER BY mp.user_id) FILTER (WHERE mp.team_no = 2) AS t2
                    FROM matches mt
                    JOIN match_results r ON r.match_id = mt.id
                    JOIN match_players mp ON mp.match_id = mt.id
                    WHERE mt.status = 'completed'
                      AND mt.id IN (SELECT match_id FROM match_players WHERE user_id = d.requested_by)
                    GROUP BY mt.id, mt.scheduled_at, mt.created_at, r.winning_team
                    HAVING count(*) = 4
                ) p ON ((p.t1 = o.t1 AND p.t2 = o.t2) OR (p.t1 = o.t2 AND p.t2 = o.t1))
                   AND p.played_at >= o.created_at
                   AND p.played_at < o.created_at + make_interval(days => ?)
                ORDER BY p.played_at
                LIMIT 1
            ) rec ON true
            WHERE d.recommended AND d.batch_id IS NOT NULL AND u.email NOT LIKE '%@bwf.example'
            """;

    private static final String COLUMNS = """
            SELECT d.batch_id, d.created_at, d.match_type, d.scoring_source, m.model_version,
                   d.team1_strength, d.team2_strength, d.balance_score,
                   d.detail_json -> 'option' ->> 'balanceLabel' AS balance_label,
                   (d.detail_json -> 'option' ->> 'team1WinProbability')::numeric AS p1,
                   d.detail_json -> 'players' AS players_json,
                   (SELECT count(*) FROM double_team_suggestions c WHERE c.batch_id = d.batch_id) AS option_count,
                   u.id AS requester_id, u.full_name AS requester_name, u.email AS requester_email,
                   rec.match_id, rec.played_at, rec.rank_no AS played_rank, rec.recommended AS played_recommended,
                   rec.correct AS played_correct
            """;

    @Transactional(readOnly = true)
    public PageResponse<Item> list(UUID adminId, String q, String type, String played, String source,
                                   LocalDate from, LocalDate to, int page, int size) {
        accessService.requirePermission(adminId, PERMISSION);
        MatchingRange range = MatchingRange.of(from, to);
        String scoringSource = MatchingRange.source(source);
        String matchType = type == null || type.isBlank() || "all".equals(type) ? null : type;
        if (matchType != null && !TYPES.contains(matchType)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Loai doi khong hop le.",
                    Map.of("type", "Chi nhan mens, womens hoac mixed"));
        }
        String playedFilter = played == null || played.isBlank() ? "all" : played;
        if (!PLAYED.contains(playedFilter)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai doi chieu khong hop le.",
                    Map.of("played", "Chi nhan all, played hoac unplayed"));
        }

        StringBuilder where = new StringBuilder(" AND d.created_at >= ? AND d.created_at < ?");
        List<Object> args = new ArrayList<>();
        args.add(AdminTeamAccuracyService.RECONCILE_DAYS);
        args.add(Timestamp.from(range.fromInstant()));
        args.add(Timestamp.from(range.toInstant()));
        if (scoringSource != null) {
            where.append(" AND d.scoring_source = ?");
            args.add(scoringSource);
        }
        if (matchType != null) {
            where.append(" AND d.match_type = ?");
            args.add(matchType);
        }
        if (q != null && !q.isBlank()) {
            where.append("""
                     AND (strpos(lower(u.full_name), lower(?)) > 0 OR strpos(lower(u.email), lower(?)) > 0
                          OR EXISTS (SELECT 1 FROM jsonb_array_elements(d.detail_json -> 'players') pl
                                     WHERE strpos(lower(pl ->> 'fullName'), lower(?)) > 0))
                    """);
            String term = q.trim();
            for (int i = 0; i < 3; i++) args.add(term);
        }
        if ("played".equals(playedFilter)) where.append(" AND rec.match_id IS NOT NULL");
        if ("unplayed".equals(playedFilter)) where.append(" AND rec.match_id IS NULL");

        Long total = jdbc.queryForObject("SELECT count(*) " + BASE + where, Long.class, args.toArray());
        long totalElements = total == null ? 0 : total;
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<Item> items = jdbc.query(COLUMNS + BASE + where + " ORDER BY d.created_at DESC, d.batch_id LIMIT ? OFFSET ?",
                (rs, row) -> item(rs), pageArgs.toArray());
        return new PageResponse<>(items, page, size, totalElements, (int) Math.ceil(totalElements / (double) size));
    }

    @Transactional(readOnly = true)
    public Detail get(UUID adminId, UUID batchId) {
        accessService.requirePermission(adminId, PERMISSION);
        List<Item> found = jdbc.query(COLUMNS + BASE + " AND d.batch_id = ?", (rs, row) -> item(rs),
                AdminTeamAccuracyService.RECONCILE_DAYS, batchId);
        if (found.isEmpty()) {
            throw ApiException.notFound("Khong tim thay lan chia doi.");
        }
        List<JsonNode> details = jdbc.query("SELECT detail_json::text FROM double_team_suggestions WHERE batch_id = ? ORDER BY rank_no",
                (rs, row) -> read(rs.getString(1)), batchId);
        JsonNode shared = details.getFirst();
        List<DoubleTeamSuggestionResponse.Player> players = new ArrayList<>();
        shared.path("players").forEach(node -> players.add(objectMapper.convertValue(node, DoubleTeamSuggestionResponse.Player.class)));
        List<DoubleTeamSuggestionResponse.Pair> pairs = new ArrayList<>();
        shared.path("pairs").forEach(node -> pairs.add(objectMapper.convertValue(node, DoubleTeamSuggestionResponse.Pair.class)));
        List<DoubleTeamSuggestionResponse.Option> options = new ArrayList<>();
        details.forEach(node -> options.add(objectMapper.convertValue(node.path("option"), DoubleTeamSuggestionResponse.Option.class)));
        return new Detail(found.getFirst(), players, pairs, options);
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("detail_json khong hop le", e);
        }
    }

    private Item item(ResultSet rs) throws SQLException {
        List<Player> players = new ArrayList<>();
        String playersJson = rs.getString("players_json");
        if (playersJson != null) {
            read(playersJson).forEach(node -> players.add(new Player(UUID.fromString(node.path("id").asText()), node.path("fullName").asText())));
        }
        UUID matchId = rs.getObject("match_id", UUID.class);
        Reconciliation reconciliation = matchId == null
                ? new Reconciliation("none", null, null, null, null, null)
                : new Reconciliation("played", matchId, instant(rs.getTimestamp("played_at")), rs.getInt("played_rank"),
                        rs.getBoolean("played_recommended"), (Boolean) rs.getObject("played_correct"));
        return new Item(rs.getObject("batch_id", UUID.class), instant(rs.getTimestamp("created_at")),
                new PlayerRef(rs.getObject("requester_id", UUID.class), rs.getString("requester_name"), rs.getString("requester_email")),
                rs.getString("match_type"), players, rs.getInt("option_count"),
                new Recommended(rs.getBigDecimal("team1_strength"), rs.getBigDecimal("team2_strength"),
                        rs.getBigDecimal("balance_score"), rs.getString("balance_label"), rs.getBigDecimal("p1")),
                rs.getString("scoring_source"), rs.getString("model_version"), reconciliation);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
