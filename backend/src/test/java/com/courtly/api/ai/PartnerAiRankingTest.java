package com.courtly.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.api.owner.OwnerApiTestBase;
import com.courtly.common.enums.ModelType;
import com.courtly.common.enums.PlayType;
import com.courtly.common.enums.PlayingStyle;
import com.courtly.domain.account.PlayerAvailabilitySlot;
import com.courtly.domain.account.PlayerAvailabilitySlotRepository;
import com.courtly.domain.account.PlayerPreferredLocation;
import com.courtly.domain.account.PlayerPreferredLocationRepository;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.courtly.domain.matching.AiModel;
import com.courtly.domain.matching.AiModelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 2.2.17 - 2.2.18, 2.2.28 - 2.2.30: cham diem bang AI, kiem tra lai ket qua, rot ve baseline va nhat ky. */
class PartnerAiRankingTest extends OwnerApiTestBase {

    private static final String[] SCHEMA = {"r_rating", "c_rating", "rating_gap", "r_win_rate", "c_win_rate", "r_matches", "c_matches",
            "r_stability", "c_stability", "r_recency_days", "c_recency_days", "r_avg_point_diff", "c_avg_point_diff", "skill_similarity",
            "location_score", "time_score", "style_score", "distance_km", "same_play_type", "r_provisional", "c_provisional"};

    @DynamicPropertySource
    static void ai(DynamicPropertyRegistry registry) {
        AiTestSupport.register(registry);
    }

    @AfterAll
    static void stopStub() {
        // stub dung chung cho cac lop test AI trong cung JVM; khong dung o day
    }

    @Autowired private PlayerStatisticsRepository statisticsRepository;
    @Autowired private PlayerPreferredLocationRepository locationRepository;
    @Autowired private PlayerAvailabilitySlotRepository availabilityRepository;
    @Autowired private AiModelRepository modelRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;

    private final StubAiServer stub = AiTestSupport.STUB;
    private User requester;
    private String token;
    private PlayerPreferredLocation location;
    private ZonedDateTime when;
    private User near;     // Elo gan nhat
    private User mid;
    private User high;     // Elo cao nhat trong khoang cho phep
    private User newbie;   // 3 tran

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        stub.reset();
        stub.partnerVersion = "test-partner-1";
        stub.scorer = f -> Math.min(99.0, 10.0 + f.get("c_rating").asDouble() / 20.0);
        requester = player("requester", "1200.00", 12);
        token = loginAs(requester);
        location = pin(requester);
        when = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(2).withHour(18).withMinute(0).withSecond(0).withNano(0);
        near = candidate("near", "1210.00", 12);
        mid = candidate("mid", "1300.00", 12);
        high = candidate("high", "1390.00", 12);
        newbie = candidate("newbie", "1250.00", 3);
        entityManager.flush();
        entityManager.clear();
    }

    private User player(String prefix, String rating, int matches) {
        User user = persistUser(prefix, Role.PLAYER);
        user.getPlayerProfile().setPlayingStyle(PlayingStyle.BALANCED);
        user.getPlayerProfile().setPreferredPlayType(PlayType.DOUBLE);
        userRepository.saveAndFlush(user);
        PlayerStatistics statistics = new PlayerStatistics(user);
        statistics.setRatingScore(new BigDecimal(rating));
        statistics.setTotalMatches(matches);
        statisticsRepository.saveAndFlush(statistics);
        return user;
    }

    private PlayerPreferredLocation pin(User user) {
        PlayerPreferredLocation entity = new PlayerPreferredLocation();
        entity.setId(UUID.randomUUID());
        entity.setUser(user);
        entity.setLabel("Khu vuc test");
        entity.setLatitude(BigDecimal.valueOf(16.0609));
        entity.setLongitude(BigDecimal.valueOf(108.2237));
        entity.setRadiusKm(BigDecimal.valueOf(10));
        return locationRepository.saveAndFlush(entity);
    }

    private User candidate(String prefix, String rating, int matches) {
        User user = player(prefix, rating, matches);
        pin(user);
        int day = when.getDayOfWeek().getValue();
        for (int d : new int[] {day}) {
            PlayerAvailabilitySlot slot = new PlayerAvailabilitySlot();
            slot.setId(UUID.randomUUID());
            slot.setUser(user);
            slot.setDayOfWeek((short) d);
            slot.setStartTime(LocalTime.of(17, 0));
            slot.setEndTime(LocalTime.of(21, 0));
            availabilityRepository.saveAndFlush(slot);
        }
        return user;
    }

    private JsonNode search(String... extra) throws Exception {
        var request = get("/api/v1/partners/search").header("Authorization", bearer(token))
                .param("preferredLocationId", location.getId().toString()).param("scheduledAt", when.toInstant().toString())
                .param("durationMinutes", "90").param("maxRatingDifference", "300");
        for (int i = 0; i + 1 < extra.length; i += 2) request = request.param(extra[i], extra[i + 1]);
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static List<String> ids(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.get("content").forEach(item -> out.add(item.get("userId").asText()));
        return out;
    }

    private void withModel(boolean gate) {
        AiTestSupport.activeModel(modelRepository, ModelType.CATBOOST_RANKER, "test-partner-1", gate);
        entityManager.flush();
        entityManager.clear();
    }

    /** Yeu cau AI cua rieng nguoi tim trong test nay (loai yeu cau tre cua test truoc con treo o AI service gia). */
    private List<JsonNode> mine() {
        return stub.rankRequests.stream().filter(r -> requester.getId().toString().equals(r.get("requesterId").asText())).toList();
    }

    private Map<String, Object> lastRun() {
        entityManager.flush();
        return jdbc.queryForMap("SELECT scoring_source, params_json::text AS params, model_id FROM matching_runs WHERE user_id = ? ORDER BY created_at DESC LIMIT 1", requester.getId());
    }

    @Test
    @DisplayName("Co model dang hoat dong: xep theo diem AI (khong theo baseline), gui du schema dac trung kem token, luu nguon ai, model, do tin cay va ly do")
    void ranksWithAi() throws Exception {
        withModel(true);
        JsonNode page = search();
        assertThat(ids(page)).containsExactly(high.getId().toString(), mid.getId().toString(), near.getId().toString(), newbie.getId().toString());
        JsonNode first = page.get("content").get(0);
        assertThat(first.get("scoringSource").asText()).isEqualTo("ai");
        assertThat(first.get("sourceLabel").asText()).isEqualTo("Gợi ý bằng AI");
        assertThat(first.get("totalScore").decimalValue()).isEqualByComparingTo(String.valueOf(Math.min(99.0, 10.0 + 1390.0 / 20.0)));
        assertThat(first.get("confidence").decimalValue()).isEqualByComparingTo("0.82");
        assertThat(first.get("confidenceLabel").asText()).isEqualTo("Cao");
        assertThat(first.get("reasons").get(0).asText()).isEqualTo("Trình độ sát nhau");
        assertThat(first.get("listId").asText()).isNotBlank();

        assertThat(stub.tokens).containsOnly(StubAiServer.TOKEN);
        JsonNode sent = mine().getFirst();
        assertThat(sent.get("candidates")).hasSize(4);
        JsonNode features = sent.get("candidates").get(0).get("features");
        for (String name : SCHEMA) assertThat(features.has(name)).as(name).isTrue();
        assertThat(features.get("r_rating").decimalValue()).isEqualByComparingTo("1200");
        assertThat(features.get("same_play_type").asInt()).isEqualTo(1);

        entityManager.flush();
        Map<String, Object> row = jdbc.queryForMap("SELECT scoring_source, model_id, confidence_score, total_score, rank_no FROM partner_suggestions WHERE user_id = ? AND suggested_user_id = ?", requester.getId(), high.getId());
        assertThat(row.get("scoring_source")).isEqualTo("ai");
        assertThat(row.get("model_id")).isNotNull();
        assertThat(((BigDecimal) row.get("confidence_score"))).isEqualByComparingTo("0.82");
        assertThat(((Number) row.get("rank_no")).intValue()).isEqualTo(1);
        assertThat(lastRun().get("scoring_source")).isEqualTo("ai");
    }

    @Test
    @DisplayName("Nguoi duoi 5 tran khong duoc day len cao: diem AI bi chan o 70, do tin cay 'Chua du du lieu'")
    void provisionalCandidatesAreCapped() throws Exception {
        withModel(true);
        stub.scorer = f -> 95.0;
        JsonNode page = search();
        for (JsonNode item : page.get("content")) {
            if (item.get("userId").asText().equals(newbie.getId().toString())) {
                assertThat(item.get("totalScore").decimalValue()).isEqualByComparingTo("70");
                assertThat(item.get("confidenceLabel").asText()).isEqualTo("Chưa đủ dữ liệu");
            } else {
                assertThat(item.get("totalScore").decimalValue()).isEqualByComparingTo("95");
            }
        }
    }

    @Test
    @DisplayName("Chua co model dang hoat dong: baseline, nhan 'Goi y theo cong thuc', khong goi AI, ly do no_active_model")
    void noActiveModel() throws Exception {
        jdbc.update("UPDATE ai_models SET status = 'archived' WHERE status = 'active' AND model_type = 'catboost_ranker'");
        entityManager.clear();
        JsonNode page = search();
        assertThat(page.get("content").get(0).get("scoringSource").asText()).isEqualTo("baseline");
        assertThat(page.get("content").get(0).get("sourceLabel").asText()).isEqualTo("Gợi ý theo công thức");
        assertThat(page.get("content").get(0).has("confidence")).isFalse();
        assertThat(mine()).isEmpty();
        assertThat(lastRun().get("params").toString()).contains("no_active_model");
        assertThat(lastRun().get("scoring_source")).isEqualTo("baseline");
    }

    private void expectFallback(StubAiServer.Mode mode, String reason) throws Exception {
        withModel(true);
        entityManager.flush();
        jdbc.update("DELETE FROM partner_suggestions WHERE user_id = ?", requester.getId());
        jdbc.update("DELETE FROM matching_runs WHERE user_id = ?", requester.getId());
        entityManager.clear();
        stub.mode = mode;
        long started = System.currentTimeMillis();
        JsonNode page = search();
        assertThat(page.get("content")).hasSize(4);
        for (JsonNode item : page.get("content")) {
            assertThat(item.get("scoringSource").asText()).as(mode.name()).isEqualTo("baseline");
            assertThat(item.get("totalScore").decimalValue().doubleValue()).isBetween(0.0, 100.0);
        }
        Map<String, Object> run = lastRun();
        assertThat(run.get("scoring_source")).isEqualTo("baseline");
        assertThat(run.get("params").toString()).contains(reason);
        assertThat(System.currentTimeMillis() - started).isLessThan(4000);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM partner_suggestions WHERE user_id = ? AND scoring_source = 'ai'", Integer.class, requester.getId())).isZero();
    }

    @Test
    @DisplayName("AI qua han (timeout), 503, sai phien ban model: van tra ket qua bang baseline va ghi ly do")
    void fallsBackWhenAiFails() throws Exception {
        expectFallback(StubAiServer.Mode.SLOW, "timeout");
        expectFallback(StubAiServer.Mode.UNAVAILABLE, "http_503");
        expectFallback(StubAiServer.Mode.WRONG_VERSION, "model_mismatch");
    }

    @Test
    @DisplayName("Ket qua AI sai (thieu ung vien, them ung vien la, trung lap, diem ngoai 0-100) bi coi la loi, dung baseline")
    void rejectsInvalidAiResults() throws Exception {
        expectFallback(StubAiServer.Mode.DROP_ONE, "invalid_result");
        expectFallback(StubAiServer.Mode.EXTRA_ONE, "invalid_result");
        expectFallback(StubAiServer.Mode.BAD_SCORE, "invalid_result");
    }

    @Test
    @DisplayName("Cac trang sau cua cung mot lan tim dung lai danh sach da xep (listId) du model/diem thay doi giua chung")
    void pagesShareOneRankedList() throws Exception {
        withModel(true);
        JsonNode first = search("size", "2", "page", "0");
        String listId = first.get("content").get(0).get("listId").asText();
        assertThat(first.get("totalElements").asInt()).isEqualTo(4);
        stub.scorer = f -> 100.0 - f.get("c_rating").asDouble() / 20.0; // dao nguoc neu goi lai AI
        JsonNode second = search("size", "2", "page", "1", "listId", listId);
        assertThat(ids(first)).containsExactly(high.getId().toString(), mid.getId().toString());
        assertThat(ids(second)).containsExactly(near.getId().toString(), newbie.getId().toString());
        assertThat(mine()).hasSize(1);
        JsonNode fresh = search("size", "2", "page", "0");
        assertThat(ids(fresh).getFirst()).isEqualTo(near.getId().toString());
    }

    @Test
    @DisplayName("Danh sach da xep chi dung cho nguoi tim da tao ra no; listId la tu nguoi khac bi bo qua va tinh lai")
    void listIdIsPrivate() throws Exception {
        withModel(true);
        String listId = search().get("content").get(0).get("listId").asText();
        User other = player("other", "1200.00", 12);
        PlayerPreferredLocation otherLocation = pin(other);
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get("/api/v1/partners/search").header("Authorization", bearer(loginAs(other)))
                .param("preferredLocationId", otherLocation.getId().toString()).param("scheduledAt", when.toInstant().toString())
                .param("durationMinutes", "90").param("listId", listId)).andExpect(status().isOk());
        assertThat(mine()).hasSize(1);
    }

    @Test
    @DisplayName("Danh sach da xep hang luu o DB (khong mat khi khoi dong lai); het han sau 10 phut thi tinh lai")
    void listIsPersistedAndExpires() throws Exception {
        withModel(true);
        String listId = search().get("content").get(0).get("listId").asText();
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT scoring_source || '|' || (expires_at > now() + interval '9 minutes')::text FROM partner_search_lists WHERE id = ?::uuid AND requester_id = ?",
                String.class, listId, requester.getId())).isEqualTo("ai|true");
        jdbc.update("UPDATE partner_search_lists SET expires_at = now() - interval '1 second' WHERE id = ?::uuid", listId);
        entityManager.clear();
        search("listId", listId);
        assertThat(mine()).hasSize(2);
    }
}
