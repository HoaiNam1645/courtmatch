package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 2.3.73 - hieu suat xu ly va chat luong model ghep cap. */
class AdminMatchingPerformanceTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/matching/performance";
    private static final String DAY = "2020-03-10";

    @Autowired private JdbcTemplate jdbc;

    private String adminToken;
    private User a;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        adminToken = loginAs(persistAdmin("perf-admin"));
        a = persistUser("perf-a", Role.PLAYER);
        // Chi con model baseline: bo model AI dang hoat dong that de ket qua on dinh (rollback cuoi test).
        jdbc.update("DELETE FROM ai_models WHERE model_type = 'catboost_ranker'");
    }

    private JsonNode performance(String from, String to) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken))
                        .param("from", from).param("to", to))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void run(String type, String status, int durationMs, int results, String createdAt) {
        jdbc.update("""
                INSERT INTO matching_runs (run_type, user_id, scoring_source, result_count, duration_ms, status, created_at)
                VALUES (?, ?, 'baseline', ?, ?, ?, ?::timestamptz)
                """, type, a.getId(), results, durationMs, status, createdAt);
    }

    private List<String> alertCodes(JsonNode result) {
        List<String> codes = new ArrayList<>();
        result.get("alerts").forEach(alert -> codes.add(alert.get("code").asText()));
        return codes;
    }

    @Test
    @DisplayName("Thoi gian p50/p95/max, ty le loi, ty le baseline va so ket qua trung binh theo tung loai")
    void summarisesRuns() throws Exception {
        for (int i = 1; i <= 18; i++) run("partner_search", "success", i * 100, 5, DAY + "T09:00:00+07");
        run("partner_search", "failed", 50, 0, DAY + "T10:00:00+07");
        run("partner_search", "failed", 50, 0, DAY + "T11:00:00+07");
        run("double_team", "success", 40, 3, DAY + "T12:00:00+07");

        JsonNode result = performance(DAY, DAY);
        JsonNode partner = result.get("partnerSearch");
        assertThat(partner.get("runs").asLong()).isEqualTo(20);
        assertThat(partner.get("failed").asLong()).isEqualTo(2);
        assertThat(partner.get("failureRate").asDouble()).isEqualTo(10.0);
        assertThat(partner.get("p50Ms").asLong()).isEqualTo(850);
        assertThat(partner.get("p95Ms").asLong()).isEqualTo(1705);
        assertThat(partner.get("maxMs").asLong()).isEqualTo(1800);
        assertThat(partner.get("baselineRuns").asLong()).isEqualTo(20);
        assertThat(partner.get("baselineRate").asDouble()).isEqualTo(100.0);
        assertThat(partner.get("avgResults").asDouble()).isEqualTo(5.0);

        JsonNode team = result.get("doubleTeam");
        assertThat(team.get("runs").asLong()).isEqualTo(1);
        assertThat(team.get("p50Ms").asLong()).isEqualTo(40);
        assertThat(team.get("avgResults").asDouble()).isEqualTo(3.0);

        assertThat(result.get("daily")).hasSize(1);
        JsonNode day = result.get("daily").get(0);
        assertThat(day.get("day").asText()).isEqualTo(DAY);
        assertThat(day.get("partnerSearches").asLong()).isEqualTo(20);
        assertThat(day.get("doubleTeams").asLong()).isEqualTo(1);
        assertThat(day.get("failed").asLong()).isEqualTo(2);

        assertThat(alertCodes(result)).contains("no_ai_service", "high_failure_rate", "slow_response");
        assertThat(result.get("aiServiceActive").asBoolean()).isFalse();
        assertThat(result.get("thresholds").get("maxFailureRate").asDouble()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Khong canh bao ty le khi chua du so lan chay; ngay khong co du lieu hien 0; khong co p50 khi khong co lan nao")
    void noAlertsOnSmallSamples() throws Exception {
        run("partner_search", "failed", 9000, 0, DAY + "T09:00:00+07");
        JsonNode result = performance("2020-03-09", "2020-03-11");
        assertThat(alertCodes(result)).containsExactly("no_ai_service");
        assertThat(result.get("daily")).hasSize(3);
        assertThat(result.get("daily").get(0).get("partnerSearches").asLong()).isZero();
        assertThat(result.get("daily").get(1).get("failed").asLong()).isEqualTo(1);

        JsonNode empty = performance("2019-01-01", "2019-01-02");
        assertThat(empty.get("partnerSearch").get("runs").asLong()).isZero();
        assertThat(empty.get("partnerSearch").has("p50Ms")).isFalse();
        assertThat(empty.get("partnerSearch").has("failureRate")).isFalse();
        assertThat(empty.get("doubleTeam").has("avgResults")).isFalse();
    }

    @Test
    @DisplayName("Model active qua cu bi canh bao, hien metrics; co model catboost active thi het canh bao khong co AI")
    void warnsAboutStaleModels() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_models (id, model_type, model_version, status, trained_at, activated_at, metrics_json)
                VALUES (?, 'catboost_ranker', ?, 'active', '2019-01-01T00:00:00Z', '2019-01-02T00:00:00Z', '{"ndcg": 0.82}'::jsonb)
                """, id, "test-stale-" + id);
        JsonNode result = performance(DAY, DAY);
        assertThat(result.get("aiServiceActive").asBoolean()).isTrue();
        assertThat(alertCodes(result)).contains("model_stale").doesNotContain("no_ai_service");
        JsonNode model = null;
        for (JsonNode item : result.get("models")) if (item.get("id").asText().equals(id.toString())) model = item;
        assertThat(model).isNotNull();
        assertThat(model.get("metrics").get("ndcg").asDouble()).isEqualTo(0.82);
        assertThat(model.get("ageDays").asLong()).isGreaterThan(90);
    }

    @Test
    @DisplayName("Ty le chap nhan thap (du mau) bi canh bao")
    void warnsAboutLowAcceptance() throws Exception {
        User b = persistUser("perf-b", Role.PLAYER);
        for (int minute = 0; minute < 30; minute++) {
            jdbc.update("""
                    INSERT INTO partner_suggestions (user_id, suggested_user_id, total_score, rank_no, scoring_source, created_at)
                    VALUES (?, ?, 60, 1, 'baseline', ?::timestamptz)
                    """, a.getId(), b.getId(), "%sT09:%02d:00+07".formatted(DAY, minute));
        }
        jdbc.update("""
                INSERT INTO partner_requests (requester_id, receiver_id, status, created_at, responded_at)
                VALUES (?, ?, 'rejected', '2020-03-11T09:00:00+07', '2020-03-11T10:00:00+07')
                """, a.getId(), b.getId());
        assertThat(alertCodes(performance(DAY, DAY))).contains("low_acceptance");
    }

    @Test
    @DisplayName("Khoang ngay sai va quyen truy cap")
    void validatesAccess() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("from", "2020-03-10").param("to", "2020-03-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(a)))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }
}
