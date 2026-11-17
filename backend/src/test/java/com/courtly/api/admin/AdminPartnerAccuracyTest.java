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

/** 2.3.70 - do chinh xac ghep cap nguoi choi bang ket qua that cua goi y. */
class AdminPartnerAccuracyTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/matching/partner-accuracy";
    /** Ngay co dinh trong qua khu: khong dung du lieu that cua database dung chung. */
    private static final String DAY = "2020-03-10";

    @Autowired private JdbcTemplate jdbc;

    private String adminToken;
    private User a;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        adminToken = loginAs(persistAdmin("acc-admin"));
        a = persistUser("acc-a", Role.PLAYER);
    }

    private JsonNode accuracy(String... params) throws Exception {
        var request = get(PATH).header("Authorization", bearer(adminToken))
                .param("from", DAY).param("to", DAY);
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void suggest(User viewer, User suggested, int rank, double score, String createdAt) {
        jdbc.update("""
                INSERT INTO partner_suggestions (user_id, suggested_user_id, total_score, rank_no, scoring_source, created_at)
                VALUES (?, ?, ?, ?, 'baseline', ?::timestamptz)
                """, viewer.getId(), suggested.getId(), score, rank, createdAt);
    }

    private UUID request(User from, User to, String status, String createdAt, String respondedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO partner_requests (id, requester_id, receiver_id, status, created_at, responded_at)
                VALUES (?, ?, ?, ?, ?::timestamptz, ?::timestamptz)
                """, id, from.getId(), to.getId(), status, createdAt, respondedAt);
        return id;
    }

    private User player(String prefix) {
        return persistUser(prefix, Role.PLAYER);
    }

    @Test
    @DisplayName("Tinh dung ty le gui yeu cau, chap nhan, tu choi, het han theo tung goi y va tong")
    void computesOutcomes() throws Exception {
        User b = player("acc-b");
        User c = player("acc-c");
        User d = player("acc-d");
        User e = player("acc-e");
        suggest(a, b, 1, 90, DAY + "T09:00:00+07");
        suggest(a, c, 2, 55, DAY + "T09:00:00+07");
        suggest(a, d, 5, 20, DAY + "T09:00:00+07");
        suggest(a, e, 12, 75, DAY + "T09:00:00+07");
        request(a, b, "accepted", "2020-03-11T09:00:00+07", "2020-03-11T10:00:00+07");
        request(a, c, "rejected", "2020-03-11T09:00:00+07", "2020-03-11T10:00:00+07");
        request(a, e, "expired", "2020-03-11T09:00:00+07", null);

        JsonNode result = accuracy();
        JsonNode all = result.get("overall");
        assertThat(all.get("suggestions").asLong()).isEqualTo(4);
        assertThat(all.get("requested").asLong()).isEqualTo(3);
        assertThat(all.get("requestRate").asDouble()).isEqualTo(75.0);
        assertThat(all.get("accepted").asLong()).isEqualTo(1);
        assertThat(all.get("rejected").asLong()).isEqualTo(1);
        assertThat(all.get("expired").asLong()).isEqualTo(1);
        assertThat(all.get("answered").asLong()).isEqualTo(2);
        assertThat(all.get("acceptanceRate").asDouble()).isEqualTo(50.0);
        assertThat(all.get("sufficient").asBoolean()).isFalse();
        assertThat(result.get("minSample").asInt()).isEqualTo(30);

        JsonNode byRank = result.get("byRank");
        assertThat(byRank.get(0).get("label").asText()).isEqualTo("Top 1");
        assertThat(byRank.get(0).get("metrics").get("suggestions").asLong()).isEqualTo(1);
        assertThat(byRank.get(0).get("metrics").get("acceptanceRate").asDouble()).isEqualTo(100.0);
        assertThat(byRank.get(1).get("metrics").get("suggestions").asLong()).isEqualTo(2);
        assertThat(byRank.get(1).get("metrics").get("acceptanceRate").asDouble()).isEqualTo(50.0);
        assertThat(byRank.get(2).get("metrics").get("suggestions").asLong()).isEqualTo(3);

        JsonNode byScore = result.get("byScore");
        assertThat(byScore.get(0).get("label").asText()).isEqualTo("0-40");
        assertThat(byScore.get(0).get("metrics").get("suggestions").asLong()).isEqualTo(1);
        assertThat(byScore.get(0).get("metrics").get("requested").asLong()).isZero();
        assertThat(byScore.get(1).get("metrics").get("suggestions").asLong()).isEqualTo(1);
        assertThat(byScore.get(2).get("metrics").get("suggestions").asLong()).isEqualTo(2);
        assertThat(byScore.get(2).get("metrics").get("requested").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("Mau so bang 0 thi ty le la null, khong chia cho 0")
    void emptyRangeHasNullRates() throws Exception {
        JsonNode all = accuracy().get("overall");
        assertThat(all.get("suggestions").asLong()).isZero();
        assertThat(all.has("requestRate")).isFalse();
        assertThat(all.has("acceptanceRate")).isFalse();
        assertThat(all.has("playedRate")).isFalse();
    }

    @Test
    @DisplayName("Yeu cau chi gan cho goi y moi nhat cua cung cap truoc yeu cau")
    void attributesToLatestSuggestion() throws Exception {
        User b = player("acc-latest");
        suggest(a, b, 1, 80, DAY + "T08:00:00+07");
        suggest(a, b, 1, 80, DAY + "T20:00:00+07");
        request(a, b, "accepted", "2020-03-11T09:00:00+07", "2020-03-11T10:00:00+07");

        JsonNode all = accuracy().get("overall");
        assertThat(all.get("suggestions").asLong()).isEqualTo(2);
        assertThat(all.get("requested").asLong()).isEqualTo(1);
        assertThat(all.get("accepted").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("Yeu cau gui sau 7 ngay hoac truoc goi y khong duoc tinh cho goi y")
    void attributionWindowIsSevenDays() throws Exception {
        User late = player("acc-late");
        User early = player("acc-early");
        suggest(a, late, 1, 80, DAY + "T09:00:00+07");
        suggest(a, early, 1, 80, DAY + "T09:00:00+07");
        request(a, late, "accepted", "2020-03-18T09:30:00+07", "2020-03-18T10:00:00+07");
        request(a, early, "accepted", "2020-03-09T09:00:00+07", "2020-03-09T10:00:00+07");

        JsonNode all = accuracy().get("overall");
        assertThat(all.get("suggestions").asLong()).isEqualTo(2);
        assertThat(all.get("requested").asLong()).isZero();
    }

    @Test
    @DisplayName("Tinh ty le choi that: hai nguoi co tran hoan thanh chung sau khi chap nhan")
    void countsPlayedTogether() throws Exception {
        User played = player("acc-played");
        User notPlayed = player("acc-not-played");
        suggest(a, played, 1, 85, DAY + "T09:00:00+07");
        suggest(a, notPlayed, 2, 85, DAY + "T09:00:00+07");
        request(a, played, "accepted", "2020-03-11T09:00:00+07", "2020-03-11T10:00:00+07");
        request(a, notPlayed, "accepted", "2020-03-11T09:00:00+07", "2020-03-11T10:00:00+07");
        // Tran truoc khi chap nhan va tran chua hoan thanh khong tinh.
        match(a, played, "completed", "2020-03-05T18:00:00+07");
        match(a, notPlayed, "cancelled", "2020-03-20T18:00:00+07");
        JsonNode before = accuracy().get("overall");
        assertThat(before.get("playedTogether").asLong()).isZero();

        match(a, played, "completed", "2020-03-15T18:00:00+07");
        JsonNode all = accuracy().get("overall");
        assertThat(all.get("accepted").asLong()).isEqualTo(2);
        assertThat(all.get("playedTogether").asLong()).isEqualTo(1);
        assertThat(all.get("playedRate").asDouble()).isEqualTo(50.0);
    }

    private void match(User x, User y, String status, String scheduledAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO matches (id, match_type, scheduled_at, status) VALUES (?, 'single', ?::timestamptz, ?)",
                id, scheduledAt, status);
        jdbc.update("INSERT INTO match_players (match_id, user_id, team_no) VALUES (?, ?, 1)", id, x.getId());
        jdbc.update("INSERT INTO match_players (match_id, user_id, team_no) VALUES (?, ?, 2)", id, y.getId());
    }

    @Test
    @DisplayName("Du lieu mau @bwf.example khong duoc tinh")
    void excludesSampleAccounts() throws Exception {
        User sample = player("acc-sample");
        jdbc.update("UPDATE users SET email = ? WHERE id = ?", "sample-" + UUID.randomUUID() + "@bwf.example", sample.getId());
        suggest(a, sample, 1, 80, DAY + "T09:00:00+07");
        assertThat(accuracy().get("overall").get("suggestions").asLong()).isZero();
    }

    @Test
    @DisplayName("Loc theo nguon cham diem; nguon la bi tu choi")
    void filtersBySource() throws Exception {
        User b = player("acc-source");
        suggest(a, b, 1, 80, DAY + "T09:00:00+07");
        assertThat(accuracy("source", "baseline").get("overall").get("suggestions").asLong()).isEqualTo(1);
        assertThat(accuracy("source", "ai").get("overall").get("suggestions").asLong()).isZero();
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("source", "gpt"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Khoang ngay sai bi tu choi; nguoi choi khong phai admin bi 403; chua dang nhap 401")
    void validatesAccess() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken))
                        .param("from", "2020-03-10").param("to", "2020-03-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken))
                        .param("from", "2018-01-01").param("to", "2020-03-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(a)))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }
}
