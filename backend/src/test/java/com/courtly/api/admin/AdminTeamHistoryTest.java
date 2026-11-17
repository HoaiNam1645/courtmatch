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

/** 2.3.76 - lich su chia doi cho quan tri vien. */
class AdminTeamHistoryTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/matching/team-history";
    private static final String DAY = "2020-03-10";

    @Autowired private JdbcTemplate jdbc;

    private String adminToken;
    private User w;
    private User x;
    private User y;
    private User z;
    private UUID modelId;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        adminToken = loginAs(persistAdmin("thist-admin"));
        w = persistUser("thist-w", Role.PLAYER);
        x = persistUser("thist-x", Role.PLAYER);
        y = persistUser("thist-y", Role.PLAYER);
        z = persistUser("thist-z", Role.PLAYER);
        modelId = UUID.randomUUID();
        jdbc.update("INSERT INTO ai_models (id, model_type, model_version, status) VALUES (?, 'double_team_ranker', ?, 'ready')",
                modelId, "test-thist-" + modelId);
    }

    private JsonNode list(String... params) throws Exception {
        var request = get(PATH).header("Authorization", bearer(adminToken)).param("from", DAY).param("to", DAY);
        for (int i = 0; i < params.length; i += 2) request = request.param(params[i], params[i + 1]);
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static String ids(User... users) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < users.length; i++) out.append(i == 0 ? "" : ",").append('"').append(users[i].getId()).append('"');
        return out.append(']').toString();
    }

    private String players() {
        StringBuilder out = new StringBuilder("[");
        User[] all = {w, x, y, z};
        for (int i = 0; i < all.length; i++) {
            out.append(i == 0 ? "" : ",").append("{\"id\":\"").append(all[i].getId()).append("\",\"fullName\":\"")
                    .append(all[i].getFullName()).append("\"}");
        }
        return out.append(']').toString();
    }

    /** Mot lan chia 3 phuong an (w,x | y,z), (w,y | x,z), (w,z | x,y) do `requester` yeu cau. */
    private UUID batch(User requester, String type, String at, double p1) {
        UUID batch = UUID.randomUUID();
        String[][] splits = {{ids(w, x), ids(y, z)}, {ids(w, y), ids(x, z)}, {ids(w, z), ids(x, y)}};
        double[] gaps = {10, 40, 80};
        String[] labels = {"Rất cân bằng", "Cân bằng", "Chênh lệch"};
        for (int rank = 1; rank <= 3; rank++) {
            String option = "{\"rank\":%d,\"recommended\":%s,\"team1\":{\"playerIds\":%s,\"strength\":1050,\"compatibility\":70},"
                    .formatted(rank, rank == 1, splits[rank - 1][0])
                    + "\"team2\":{\"playerIds\":%s,\"strength\":1040,\"compatibility\":60},\"balanceScore\":%s,\"balanceLabel\":\"%s\",\"team1WinProbability\":%s,\"reason\":\"ly do %d\"}"
                    .formatted(splits[rank - 1][1], gaps[rank - 1], labels[rank - 1], rank == 1 ? p1 : 50.0, rank);
            String detail = "{\"players\":" + players() + ",\"pairs\":[{\"playerIds\":" + ids(w, x) + ",\"totalScore\":77}],\"option\":" + option + "}";
            jdbc.update("""
                    INSERT INTO double_team_suggestions (model_id, requested_by, batch_id, match_type, rank_no, recommended,
                           scoring_source, team1_players, team2_players, team1_strength, team2_strength, balance_score,
                           detail_json, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'baseline', ?::jsonb, ?::jsonb, 1050, 1040, ?, ?::jsonb, ?::timestamptz)
                    """, modelId, requester.getId(), batch, type, rank, rank == 1, splits[rank - 1][0], splits[rank - 1][1],
                    gaps[rank - 1], detail, at);
        }
        return batch;
    }

    private UUID match(String playedAt, int winningTeam, User[] team1, User[] team2) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO matches (id, match_type, scheduled_at, status) VALUES (?, 'double', ?::timestamptz, 'completed')", id, playedAt);
        for (User u : team1) jdbc.update("INSERT INTO match_players (match_id, user_id, team_no) VALUES (?, ?, 1)", id, u.getId());
        for (User u : team2) jdbc.update("INSERT INTO match_players (match_id, user_id, team_no) VALUES (?, ?, 2)", id, u.getId());
        jdbc.update("INSERT INTO match_results (match_id, winning_team) VALUES (?, ?)", id, winningTeam);
        return id;
    }

    @Test
    @DisplayName("Moi lan chia: nguoi yeu cau, loai doi, 4 nguoi, phuong an khuyen nghi, nguon cham, ket qua doi chieu; moi nhat truoc")
    void listsBatches() throws Exception {
        UUID older = batch(w, "mens", DAY + "T08:00:00+07", 64.0);
        UUID newer = batch(x, "mixed", DAY + "T09:00:00+07", 64.0);
        // Tran that theo phuong an 1 cua lan cu (doi (y,z) la doi 1, thang) -> dao so doi, doi (w,x) thua: du doan sai.
        UUID matchId = match("2020-03-12T18:00:00+07", 1, new User[] {y, z}, new User[] {w, x});

        JsonNode result = list();
        assertThat(result.get("totalElements").asLong()).isEqualTo(2);
        JsonNode first = result.get("content").get(0);
        assertThat(first.get("batchId").asText()).isEqualTo(newer.toString());
        assertThat(first.get("matchType").asText()).isEqualTo("mixed");
        assertThat(first.get("requester").get("email").asText()).isEqualTo(x.getEmail());
        assertThat(first.get("players")).hasSize(4);
        assertThat(first.get("players").get(0).get("fullName").asText()).isEqualTo(w.getFullName());
        assertThat(first.get("optionCount").asInt()).isEqualTo(3);
        assertThat(first.get("scoringSource").asText()).isEqualTo("baseline");
        assertThat(first.get("modelVersion").asText()).startsWith("test-thist-");
        JsonNode recommended = first.get("recommended");
        assertThat(recommended.get("balanceScore").asDouble()).isEqualTo(10.0);
        assertThat(recommended.get("balanceLabel").asText()).isEqualTo("Rất cân bằng");
        assertThat(recommended.get("team1WinProbability").asDouble()).isEqualTo(64.0);
        // Moi hai lan chia deu co mot phuong an trung cach chia cua tran that; nguoi yeu cau phai nam trong tran.
        JsonNode olderItem = result.get("content").get(1);
        assertThat(olderItem.get("batchId").asText()).isEqualTo(older.toString());
        JsonNode reconciliation = olderItem.get("reconciliation");
        assertThat(reconciliation.get("status").asText()).isEqualTo("played");
        assertThat(reconciliation.get("matchId").asText()).isEqualTo(matchId.toString());
        assertThat(reconciliation.get("optionRank").asInt()).isEqualTo(1);
        assertThat(reconciliation.get("optionRecommended").asBoolean()).isTrue();
        // Phuong an 1: doi 1 = (w,x) du doan thang (64%) nhung that ra (w,x) thua -> sai.
        assertThat(reconciliation.get("predictedCorrect").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Chua co tran khop thi trang thai none; tran chua xac nhan hoac nguoi khac khong yeu cau khong tinh")
    void unplayedBatches() throws Exception {
        UUID id = batch(w, "mens", DAY + "T08:00:00+07", 64.0);
        UUID scheduled = UUID.randomUUID();
        jdbc.update("INSERT INTO matches (id, match_type, scheduled_at, status) VALUES (?, 'double', '2020-03-12T18:00:00+07'::timestamptz, 'scheduled')", scheduled);
        for (User u : new User[] {w, x}) jdbc.update("INSERT INTO match_players (match_id, user_id, team_no) VALUES (?, ?, 1)", scheduled, u.getId());
        for (User u : new User[] {y, z}) jdbc.update("INSERT INTO match_players (match_id, user_id, team_no) VALUES (?, ?, 2)", scheduled, u.getId());

        JsonNode item = list().get("content").get(0);
        assertThat(item.get("batchId").asText()).isEqualTo(id.toString());
        assertThat(item.get("reconciliation").get("status").asText()).isEqualTo("none");
        assertThat(item.get("reconciliation").has("matchId")).isFalse();
    }

    @Test
    @DisplayName("Loc theo loai doi, da/chua co tran, tu khoa (nguoi yeu cau hoac ten nguoi trong lan chia), nguon; phan trang")
    void filtersAndPaginates() throws Exception {
        batch(w, "mens", DAY + "T08:00:00+07", 64.0);
        batch(x, "mixed", DAY + "T09:00:00+07", 64.0);
        batch(x, "womens", DAY + "T10:00:00+07", 64.0);
        match("2020-03-12T18:00:00+07", 2, new User[] {w, x}, new User[] {y, z});

        assertThat(list().get("totalElements").asLong()).isEqualTo(3);
        assertThat(list("type", "mixed").get("totalElements").asLong()).isEqualTo(1);
        assertThat(list("type", "all").get("totalElements").asLong()).isEqualTo(3);
        assertThat(list("played", "played").get("totalElements").asLong()).isEqualTo(3); // ca ba lan co 4 nguoi nay
        assertThat(list("played", "unplayed").get("totalElements").asLong()).isZero();
        assertThat(list("q", "thist-w").get("totalElements").asLong()).isEqualTo(3); // ten nguoi trong lan chia / nguoi yeu cau
        assertThat(list("q", "khong-co").get("totalElements").asLong()).isZero();
        assertThat(list("source", "ai").get("totalElements").asLong()).isZero();
        assertThat(list("source", "baseline").get("totalElements").asLong()).isEqualTo(3);

        JsonNode page = list("size", "2", "page", "1");
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("content").get(0).get("matchType").asText()).isEqualTo("mens");
    }

    @Test
    @DisplayName("Chi tiet: du 3 phuong an theo thu tu, suc manh va tuong thich; 404 khi khong co")
    void showsDetail() throws Exception {
        UUID id = batch(w, "mens", DAY + "T08:00:00+07", 64.0);
        JsonNode detail = objectMapper.readTree(mockMvc.perform(get(PATH + "/" + id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(detail.get("item").get("batchId").asText()).isEqualTo(id.toString());
        assertThat(detail.get("players")).hasSize(4);
        assertThat(detail.get("pairs")).hasSize(1);
        assertThat(detail.get("options")).hasSize(3);
        assertThat(detail.get("options").get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(detail.get("options").get(0).get("recommended").asBoolean()).isTrue();
        assertThat(detail.get("options").get(2).get("balanceLabel").asText()).isEqualTo("Chênh lệch");
        assertThat(detail.get("options").get(1).get("reason").asText()).isEqualTo("ly do 2");
        assertThat(detail.get("options").get(0).get("team1").get("playerIds")).hasSize(2);
        mockMvc.perform(get(PATH + "/" + UUID.randomUUID()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Du lieu mau bi bo; tham so sai bi tu choi; nguoi choi khong xem duoc")
    void validatesAndProtects() throws Exception {
        UUID id = batch(w, "mens", DAY + "T08:00:00+07", 64.0);
        jdbc.update("UPDATE users SET email = ? WHERE id = ?", "sample-" + UUID.randomUUID() + "@bwf.example", w.getId());
        assertThat(list().get("totalElements").asLong()).isZero();
        mockMvc.perform(get(PATH + "/" + id).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());

        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("type", "doubles")).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("played", "maybe")).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(x)))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH + "/" + id).header("Authorization", bearer(loginAs(x)))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }
}
