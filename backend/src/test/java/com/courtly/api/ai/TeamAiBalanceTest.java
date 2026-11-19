package com.courtly.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.api.owner.OwnerApiTestBase;
import com.courtly.common.enums.Gender;
import com.courtly.common.enums.ModelType;
import com.courtly.common.enums.PlayingStyle;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.courtly.domain.matching.AiModelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 2.2.28 (chia doi): model AI du doan do can bang, kiem tra lai ket qua, rot ve baseline va nhat ky. */
class TeamAiBalanceTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/double-teams/suggestions";
    private static final List<String> SCHEMA = List.of("t1_rating", "t2_rating", "rating_diff", "t1_partner_gap", "t2_partner_gap",
            "t1_win_rate", "t2_win_rate", "t1_min_matches", "t2_min_matches", "t1_stability", "t2_stability", "t1_pair_compat",
            "t2_pair_compat", "t1_recency_days", "t2_recency_days", "is_mixed");

    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        AiTestSupport.register(registry);
    }

    @Autowired private PlayerStatisticsRepository statisticsRepository;
    @Autowired private AiModelRepository modelRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;

    private final StubAiServer stub = AiTestSupport.STUB;
    private User a;
    private User b;
    private User c;
    private User d;

    @BeforeEach
    void setUp() {
        ensureRoles();
        stub.reset();
        stub.teamVersion = "test-team-1";
        stub.probability = rank -> rank == 2 ? 0.5 : 0.8;
        a = player("a", "1300.00");
        b = player("b", "1250.00");
        c = player("c", "1100.00");
        d = player("d", "1000.00");
        entityManager.flush();
        entityManager.clear();
    }

    private User player(String prefix, String rating) {
        User user = persistUser(prefix, Role.PLAYER);
        user.getPlayerProfile().setGender(Gender.MALE);
        user.getPlayerProfile().setPlayingStyle(PlayingStyle.BALANCED);
        userRepository.saveAndFlush(user);
        PlayerStatistics statistics = new PlayerStatistics(user);
        statistics.setRatingScore(new BigDecimal(rating));
        statistics.setTotalMatches(15);
        statisticsRepository.saveAndFlush(statistics);
        return user;
    }

    private JsonNode suggest() throws Exception {
        String body = "{\"matchType\":\"mens\",\"playerIds\":[\"" + b.getId() + "\",\"" + c.getId() + "\",\"" + d.getId() + "\"]}";
        return objectMapper.readTree(mockMvc.perform(post(PATH).header("Authorization", bearer(loginAs(a)))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void withModel() {
        AiTestSupport.activeModel(modelRepository, ModelType.CATBOOST_TEAM_RANKER, "test-team-1", true);
        entityManager.flush();
        entityManager.clear();
    }

    private List<JsonNode> mine() {
        return stub.balanceRequests.stream().toList();
    }

    @Test
    @DisplayName("Co model AI chia doi: phuong an AI du doan can bang nhat duoc de xuat, nhan nguon ai, gui du schema dac trung, luu model va nguon ai")
    void aiPicksTheBalancedOption() throws Exception {
        withModel();
        JsonNode result = suggest();
        assertThat(result.get("scoringSource").asText()).isEqualTo("ai");
        assertThat(result.get("modelVersion").asText()).isEqualTo("test-team-1");
        JsonNode best = result.get("options").get(0);
        assertThat(best.get("recommended").asBoolean()).isTrue();
        assertThat(best.get("balanceScore").decimalValue()).isEqualByComparingTo("0");
        assertThat(best.get("balanceLabel").asText()).isEqualTo("Rất cân bằng");
        assertThat(best.get("team1WinProbability").decimalValue()).isEqualByComparingTo("50.0");
        assertThat(best.get("reason").asText()).contains("Mô hình AI");
        assertThat(result.get("options").get(1).get("recommended").asBoolean()).isFalse();
        assertThat(result.get("options").get(1).get("balanceScore").decimalValue().doubleValue()).isEqualTo(60.0);

        JsonNode sent = mine().getFirst();
        assertThat(sent.get("options")).hasSize(3);
        JsonNode features = sent.get("options").get(0).get("features");
        SCHEMA.forEach(name -> assertThat(features.has(name)).as(name).isTrue());
        assertThat(features.get("is_mixed").asInt()).isZero();

        entityManager.flush();
        Integer ai = jdbc.queryForObject("SELECT count(*) FROM double_team_suggestions WHERE requested_by = ? AND scoring_source = 'ai'", Integer.class, a.getId());
        assertThat(ai).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT scoring_source FROM matching_runs WHERE user_id = ? ORDER BY created_at DESC LIMIT 1", String.class, a.getId())).isEqualTo("ai");
        assertThat(jdbc.queryForObject("SELECT m.model_type FROM double_team_suggestions s JOIN ai_models m ON m.id = s.model_id WHERE s.requested_by = ? LIMIT 1", String.class, a.getId())).isEqualTo("catboost_team_ranker");
    }

    @Test
    @DisplayName("Khong co model AI chia doi (baseline chia doi van active): dung baseline, khong goi AI")
    void baselineWithoutAiModel() throws Exception {
        JsonNode result = suggest();
        assertThat(result.get("scoringSource").asText()).isEqualTo("baseline");
        assertThat(stub.balanceRequests).isEmpty();
        assertThat(result.get("options").get(0).get("reason").asText()).doesNotContain("Mô hình AI");
    }

    @Test
    @DisplayName("AI loi, sai phien ban hoac ket qua sai: TOAN BO phuong an dung baseline (khong tron hai nguon) va ghi ly do")
    void fallsBackAsAWhole() throws Exception {
        withModel();
        for (var entry : Map.of(StubAiServer.Mode.UNAVAILABLE, "http_503", StubAiServer.Mode.WRONG_VERSION, "model_mismatch",
                StubAiServer.Mode.DROP_ONE, "invalid_result", StubAiServer.Mode.BAD_SCORE, "invalid_result").entrySet()) {
            stub.mode = entry.getKey();
            entityManager.flush();
            jdbc.update("DELETE FROM double_team_suggestions WHERE requested_by = ?", a.getId());
            jdbc.update("DELETE FROM matching_runs WHERE user_id = ?", a.getId());
            entityManager.clear();
            JsonNode result = suggest();
            assertThat(result.get("scoringSource").asText()).as(entry.getKey().name()).isEqualTo("baseline");
            entityManager.flush();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM double_team_suggestions WHERE requested_by = ? AND scoring_source = 'baseline'", Integer.class, a.getId())).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT params_json::text FROM matching_runs WHERE user_id = ?", String.class, a.getId())).contains(entry.getValue());
        }
    }
}
