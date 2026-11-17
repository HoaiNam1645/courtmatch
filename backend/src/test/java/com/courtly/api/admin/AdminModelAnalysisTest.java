package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 2.3.72 - phan tich ket qua model xep hang doi tac. */
class AdminModelAnalysisTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/matching/model-analysis";
    private static final String DAY = "2020-03-10";

    @Autowired private JdbcTemplate jdbc;

    private String adminToken;
    private User a;
    private UUID modelA;
    private UUID modelB;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        adminToken = loginAs(persistAdmin("model-admin"));
        a = persistUser("model-a", Role.PLAYER);
        modelA = model("baseline_ranker", "test-a-", "ready");
        modelB = model("baseline_ranker", "test-b-", "ready");
    }

    private UUID model(String type, String versionPrefix, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ai_models (id, model_type, model_version, status) VALUES (?, ?, ?, ?)",
                id, type, versionPrefix + id, status);
        return id;
    }

    private JsonNode analysis(String... params) throws Exception {
        var request = get(PATH).header("Authorization", bearer(adminToken)).param("from", DAY).param("to", DAY);
        for (int i = 0; i < params.length; i += 2) request = request.param(params[i], params[i + 1]);
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode find(JsonNode result, UUID modelId) {
        for (JsonNode item : result.get("models")) {
            if (item.get("id").asText().equals(modelId.toString())) return item;
        }
        return null;
    }

    private User suggestTo(UUID model, double total, Double skill, Double location, Double time, Double style) {
        User b = persistUser("model-b", Role.PLAYER);
        jdbc.update("""
                INSERT INTO partner_suggestions (model_id, user_id, suggested_user_id, total_score, skill_score,
                       location_score, time_score, style_score, scoring_source, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'baseline', ?::timestamptz)
                """, model, a.getId(), b.getId(), total, skill, location, time, style, DAY + "T09:00:00+07");
        return b;
    }

    @Test
    @DisplayName("Diem trung binh, phan bo, ty le thieu du lieu va ty le chap nhan theo tung model")
    void analysesPartnerModels() throws Exception {
        User accepted = suggestTo(modelA, 90, 100.0, 80.0, 90.0, 100.0);
        suggestTo(modelA, 50, 60.0, 40.0, null, 100.0);
        suggestTo(modelA, 10, 20.0, 10.0, null, 50.0);
        suggestTo(modelB, 70, 70.0, 70.0, 70.0, 70.0);
        jdbc.update("""
                INSERT INTO partner_requests (requester_id, receiver_id, status, created_at, responded_at)
                VALUES (?, ?, 'accepted', '2020-03-11T09:00:00+07', '2020-03-11T10:00:00+07')
                """, a.getId(), accepted.getId());

        JsonNode result = analysis();
        JsonNode partnerA = find(result, modelA).get("partner");
        assertThat(partnerA.get("scored").asLong()).isEqualTo(3);
        assertThat(partnerA.get("avgTotal").asDouble()).isEqualTo(50.0);
        assertThat(partnerA.get("avgSkill").asDouble()).isEqualTo(60.0);
        assertThat(partnerA.get("avgLocation").asDouble()).isEqualTo(43.3);
        assertThat(partnerA.get("avgTime").asDouble()).isEqualTo(90.0); // chi tinh diem co gia tri
        assertThat(partnerA.get("avgStyle").asDouble()).isEqualTo(83.3);
        assertThat(partnerA.get("missingTimeRate").asDouble()).isEqualTo(66.7);
        assertThat(partnerA.get("missingLocationRate").isMissingNode() || !partnerA.has("missingLocationRate")
                || partnerA.get("missingLocationRate").asDouble() == 0.0).isTrue();
        assertThat(partnerA.get("distribution")).hasSize(5);
        assertThat(partnerA.get("distribution").get(0).get("count").asLong()).isEqualTo(1);
        assertThat(partnerA.get("distribution").get(2).get("count").asLong()).isEqualTo(1);
        assertThat(partnerA.get("distribution").get(4).get("count").asLong()).isEqualTo(1);
        assertThat(partnerA.get("distribution").get(1).get("count").asLong()).isZero();
        assertThat(partnerA.get("requested").asLong()).isEqualTo(1);
        assertThat(partnerA.get("accepted").asLong()).isEqualTo(1);
        assertThat(partnerA.get("acceptanceRate").asDouble()).isEqualTo(100.0);
        assertThat(partnerA.get("sufficient").asBoolean()).isFalse();
        assertThat(find(result, modelA).get("team")).isNull();

        JsonNode partnerB = find(result, modelB).get("partner");
        assertThat(partnerB.get("scored").asLong()).isEqualTo(1);
        assertThat(partnerB.has("acceptanceRate")).isFalse();
        assertThat(partnerB.get("requested").asLong()).isZero();
    }

    @Test
    @DisplayName("Model chia doi: lan chia, do chenh trung binh cua phuong an khuyen nghi, ty le cap thieu lich/vi tri")
    void analysesTeamModel() throws Exception {
        UUID teamModel = model("double_team_ranker", "test-t-", "ready");
        String pairs = "[{\"timeScore\":80,\"locationScore\":70},{\"locationScore\":70},{\"timeScore\":80},{},"
                + "{\"timeScore\":80,\"locationScore\":70},{\"timeScore\":80,\"locationScore\":70}]";
        for (int batch = 0; batch < 2; batch++) {
            UUID batchId = UUID.randomUUID();
            for (int rank = 1; rank <= 2; rank++) {
                jdbc.update("""
                        INSERT INTO double_team_suggestions (model_id, requested_by, batch_id, match_type, rank_no, recommended,
                               scoring_source, team1_players, team2_players, balance_score, detail_json, created_at)
                        VALUES (?, ?, ?, 'mens', ?, ?, 'baseline', '[]'::jsonb, '[]'::jsonb, ?, ?::jsonb, ?::timestamptz)
                        """, teamModel, a.getId(), batchId, rank, rank == 1, rank == 1 ? 10 + batch * 10 : 90,
                        "{\"pairs\":" + pairs + "}", DAY + "T09:00:00+07");
            }
        }
        JsonNode team = find(analysis(), teamModel).get("team");
        assertThat(team.get("batches").asLong()).isEqualTo(2);
        assertThat(team.get("avgBestGap").asDouble()).isEqualTo(15.0);
        assertThat(team.get("pairs").asLong()).isEqualTo(12);
        assertThat(team.get("missingTimeRate").asDouble()).isEqualTo(33.3);
        assertThat(team.get("missingLocationRate").asDouble()).isEqualTo(33.3);
        assertThat(find(analysis(), teamModel).get("partner")).isNull();
    }

    @Test
    @DisplayName("Model dang hoat dong luon hien, model khong co du lieu va khong hoat dong thi khong hien")
    void listsActiveModelsWithoutData() throws Exception {
        JsonNode result = analysis();
        assertThat(find(result, modelA)).isNull();
        // Chi mot model moi co the active theo tung loai; dung loai catboost de khong dung model that.
        jdbc.update("DELETE FROM ai_models WHERE model_type = 'catboost_ranker' AND status = 'active'");
        UUID active = UUID.randomUUID();
        jdbc.update("INSERT INTO ai_models (id, model_type, model_version, status, activated_at) VALUES (?, 'catboost_ranker', ?, 'active', now())",
                active, "test-active-" + active);
        JsonNode item = find(analysis(), active);
        assertThat(item).isNotNull();
        assertThat(item.get("status").asText()).isEqualTo("active");
        assertThat(item.get("partner")).isNull();
        assertThat(item.get("team")).isNull();
    }

    @Test
    @DisplayName("Du lieu mau @bwf.example bi bo, loc nguon, nguon la va quyen truy cap")
    void filtersAndAccess() throws Exception {
        User sample = suggestTo(modelA, 80, 80.0, 80.0, 80.0, 80.0);
        assertThat(find(analysis(), modelA)).isNotNull();
        jdbc.update("UPDATE users SET email = ? WHERE id = ?", "sample-" + UUID.randomUUID() + "@bwf.example", sample.getId());
        assertThat(find(analysis(), modelA)).isNull();

        suggestTo(modelA, 80, 80.0, 80.0, 80.0, 80.0);
        assertThat(find(analysis("source", "baseline"), modelA)).isNotNull();
        assertThat(find(analysis("source", "ai"), modelA)).isNull();
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("source", "x"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(a)))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }
}
