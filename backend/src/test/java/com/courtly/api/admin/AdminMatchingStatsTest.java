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

/** 2.3.74 - thong ke hieu suat ghep cap theo ngay / tuan / thang. */
class AdminMatchingStatsTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/matching/stats";

    @Autowired private JdbcTemplate jdbc;

    private String adminToken;
    private User a;
    private User b;
    private User c;
    private User d;
    private User e;
    private UUID modelId;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        adminToken = loginAs(persistAdmin("mstats-admin"));
        a = persistUser("mstats-a", Role.PLAYER);
        b = persistUser("mstats-b", Role.PLAYER);
        c = persistUser("mstats-c", Role.PLAYER);
        d = persistUser("mstats-d", Role.PLAYER);
        e = persistUser("mstats-e", Role.PLAYER);
        modelId = UUID.randomUUID();
        jdbc.update("INSERT INTO ai_models (id, model_type, model_version, status) VALUES (?, 'double_team_ranker', ?, 'ready')",
                modelId, "test-mstats-" + modelId);
        seed();
    }

    private void run(String type, String status, String at) {
        jdbc.update("INSERT INTO matching_runs (run_type, user_id, status, created_at) VALUES (?, ?, ?, ?::timestamptz)",
                type, a.getId(), status, at);
    }

    private void suggestion(User to, String at) {
        jdbc.update("INSERT INTO partner_suggestions (user_id, suggested_user_id, total_score, created_at) VALUES (?, ?, 50, ?::timestamptz)",
                a.getId(), to.getId(), at);
    }

    private void request(User from, User to, String status, String at, String respondedAt) {
        jdbc.update("INSERT INTO partner_requests (requester_id, receiver_id, status, created_at, responded_at) VALUES (?, ?, ?, ?::timestamptz, ?::timestamptz)",
                from.getId(), to.getId(), status, at, respondedAt);
    }

    private void seed() {
        run("partner_search", "success", "2020-03-09T09:00:00+07");
        run("partner_search", "success", "2020-03-09T10:00:00+07");
        run("partner_search", "failed", "2020-03-09T11:00:00+07");
        suggestion(b, "2020-03-09T09:00:00+07");
        suggestion(c, "2020-03-09T09:00:00+07");
        suggestion(d, "2020-03-09T09:00:00+07");
        request(a, b, "accepted", "2020-03-09T12:00:00+07", "2020-03-09T12:30:00+07");
        request(a, c, "rejected", "2020-03-09T12:00:00+07", "2020-03-09T13:30:00+07");

        run("partner_search", "success", "2020-03-10T09:00:00+07");
        run("double_team", "success", "2020-03-10T09:00:00+07");
        for (int rank = 1; rank <= 3; rank++) {
            jdbc.update("""
                    INSERT INTO double_team_suggestions (model_id, requested_by, batch_id, match_type, rank_no, recommended,
                           team1_players, team2_players, created_at)
                    VALUES (?, ?, '44444444-4444-4444-8444-444444444444', 'mens', ?, ?, '[]'::jsonb, '[]'::jsonb, '2020-03-10T09:00:00+07')
                    """, modelId, a.getId(), rank, rank == 1);
        }
        request(a, d, "expired", "2020-03-10T09:00:00+07", null);
        request(b, c, "cancelled", "2020-03-10T09:00:00+07", null);

        request(a, e, "pending", "2020-03-12T09:00:00+07", null);
        jdbc.update("INSERT INTO player_connections (user_id, partner_id, created_at) VALUES (?, ?, '2020-03-12T10:00:00+07'::timestamptz)", a.getId(), b.getId());
        jdbc.update("INSERT INTO player_connections (user_id, partner_id, created_at) VALUES (?, ?, '2020-03-12T10:00:00+07'::timestamptz)", b.getId(), a.getId());
    }

    private JsonNode stats(String from, String to, String granularity) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken))
                        .param("from", from).param("to", to).param("granularity", granularity))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Theo ngay: moi chi so dung o tung ngay, ngay khong co du lieu hien 0, tong khop")
    void countsPerDay() throws Exception {
        JsonNode result = stats("2020-03-09", "2020-03-12", "day");
        assertThat(result.get("granularity").asText()).isEqualTo("day");
        assertThat(result.get("points")).hasSize(4);
        JsonNode mar9 = result.get("points").get(0);
        assertThat(mar9.get("period").asText()).isEqualTo("2020-03-09");
        assertThat(mar9.get("searches").asLong()).isEqualTo(2); // lan loi khong tinh
        assertThat(mar9.get("suggestions").asLong()).isEqualTo(3);
        assertThat(mar9.get("requests").asLong()).isEqualTo(2);
        assertThat(mar9.get("accepted").asLong()).isEqualTo(1);
        assertThat(mar9.get("rejected").asLong()).isEqualTo(1);
        JsonNode mar10 = result.get("points").get(1);
        assertThat(mar10.get("searches").asLong()).isEqualTo(1);
        assertThat(mar10.get("expired").asLong()).isEqualTo(1);
        assertThat(mar10.get("cancelled").asLong()).isEqualTo(1);
        assertThat(mar10.get("teamRuns").asLong()).isEqualTo(1);
        assertThat(mar10.get("teamOptions").asLong()).isEqualTo(3);
        JsonNode mar11 = result.get("points").get(2);
        assertThat(mar11.get("searches").asLong()).isZero();
        assertThat(mar11.get("requests").asLong()).isZero();
        JsonNode mar12 = result.get("points").get(3);
        assertThat(mar12.get("requests").asLong()).isEqualTo(1);
        assertThat(mar12.get("connections").asLong()).isEqualTo(1); // hai chieu chi tinh mot ket noi

        JsonNode totals = result.get("totals");
        assertThat(totals.has("period")).isFalse();
        assertThat(totals.get("searches").asLong()).isEqualTo(3);
        assertThat(totals.get("suggestions").asLong()).isEqualTo(3);
        assertThat(totals.get("requests").asLong()).isEqualTo(5);
        assertThat(totals.get("accepted").asLong()).isEqualTo(1);
        assertThat(totals.get("rejected").asLong()).isEqualTo(1);
        assertThat(totals.get("expired").asLong()).isEqualTo(1);
        assertThat(totals.get("cancelled").asLong()).isEqualTo(1);
        assertThat(totals.get("connections").asLong()).isEqualTo(1);
        assertThat(totals.get("teamRuns").asLong()).isEqualTo(1);
        assertThat(totals.get("teamOptions").asLong()).isEqualTo(3);
        // Chi tinh yeu cau da duoc tra loi: (30 + 90) / 2 phut.
        assertThat(result.get("avgResponseMinutes").asDouble()).isEqualTo(60.0);
        assertThat(result.get("responseSamples").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("Theo tuan: bat dau thu Hai, khoang bat dau giua tuan van dung tuan chua ngay do; theo thang gop ca thang")
    void groupsByWeekAndMonth() throws Exception {
        JsonNode weeks = stats("2020-03-04", "2020-03-12", "week");
        assertThat(weeks.get("points")).hasSize(2);
        assertThat(weeks.get("points").get(0).get("period").asText()).isEqualTo("2020-03-02");
        assertThat(weeks.get("points").get(0).get("requests").asLong()).isZero();
        assertThat(weeks.get("points").get(1).get("period").asText()).isEqualTo("2020-03-09");
        assertThat(weeks.get("points").get(1).get("requests").asLong()).isEqualTo(5);
        assertThat(weeks.get("points").get(1).get("searches").asLong()).isEqualTo(3);

        JsonNode months = stats("2020-03-01", "2020-04-15", "month");
        assertThat(months.get("points")).hasSize(2);
        assertThat(months.get("points").get(0).get("period").asText()).isEqualTo("2020-03-01");
        assertThat(months.get("points").get(0).get("requests").asLong()).isEqualTo(5);
        assertThat(months.get("points").get(1).get("period").asText()).isEqualTo("2020-04-01");
        assertThat(months.get("points").get(1).get("requests").asLong()).isZero();
    }

    @Test
    @DisplayName("Khong co du lieu: moi chi so 0, thoi gian phan hoi null; du lieu mau bi bo")
    void emptyAndSampleData() throws Exception {
        JsonNode empty = stats("2019-01-01", "2019-01-03", "day");
        assertThat(empty.get("points")).hasSize(3);
        assertThat(empty.get("totals").get("requests").asLong()).isZero();
        assertThat(empty.has("avgResponseMinutes")).isFalse();
        assertThat(empty.get("responseSamples").asLong()).isZero();

        jdbc.update("UPDATE users SET email = ? WHERE id = ?", "sample-" + UUID.randomUUID() + "@bwf.example", a.getId());
        JsonNode result = stats("2020-03-09", "2020-03-12", "day");
        assertThat(result.get("totals").get("searches").asLong()).isZero();
        assertThat(result.get("totals").get("suggestions").asLong()).isZero();
        // Chi con yeu cau giua hai nguoi that (b -> c); moi thu lien quan toi a da bi bo.
        assertThat(result.get("totals").get("requests").asLong()).isEqualTo(1);
        assertThat(result.get("totals").get("connections").asLong()).isZero();
        assertThat(result.get("totals").get("teamRuns").asLong()).isZero();
    }

    @Test
    @DisplayName("Don vi thoi gian sai, khoang ngay sai va quyen truy cap")
    void validatesInput() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("granularity", "year"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("from", "2020-03-10").param("to", "2020-03-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(b)))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }
}
