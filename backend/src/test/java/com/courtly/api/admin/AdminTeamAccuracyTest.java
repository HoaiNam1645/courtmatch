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

/** 2.3.71 - do chinh xac ghep doi bang cach doi chieu voi tran that. */
class AdminTeamAccuracyTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/matching/team-accuracy";
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
        adminToken = loginAs(persistAdmin("tacc-admin"));
        w = persistUser("tacc-w", Role.PLAYER);
        x = persistUser("tacc-x", Role.PLAYER);
        y = persistUser("tacc-y", Role.PLAYER);
        z = persistUser("tacc-z", Role.PLAYER);
        modelId = UUID.randomUUID();
        jdbc.update("INSERT INTO ai_models (id, model_type, model_version, status) VALUES (?, 'double_team_ranker', ?, 'ready')",
                modelId, "test-" + modelId);
    }

    private JsonNode accuracy(String... params) throws Exception {
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

    /** Mot phuong an; probability la xac suat doi 1 thang (phan tram). */
    private void option(UUID batch, int rank, boolean recommended, double probability, double gap,
                        String team1, String team2, User requester) {
        jdbc.update("""
                INSERT INTO double_team_suggestions (model_id, requested_by, batch_id, match_type, rank_no, recommended,
                       scoring_source, team1_players, team2_players, team1_strength, team2_strength, balance_score,
                       detail_json, created_at)
                VALUES (?, ?, ?, 'mens', ?, ?, 'baseline', ?::jsonb, ?::jsonb, 1000, 1000, ?, ?::jsonb, ?::timestamptz)
                """, modelId, requester.getId(), batch, rank, recommended, team1, team2, gap,
                "{\"option\":{\"team1WinProbability\":" + probability + "}}", DAY + "T09:00:00+07");
    }

    private UUID match(String status, String playedAt, Integer winningTeam, User[] team1, User[] team2, int[][] games) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO matches (id, match_type, scheduled_at, status) VALUES (?, 'double', ?::timestamptz, ?)",
                id, playedAt, status);
        for (User u : team1) jdbc.update("INSERT INTO match_players (match_id, user_id, team_no) VALUES (?, ?, 1)", id, u.getId());
        for (User u : team2) jdbc.update("INSERT INTO match_players (match_id, user_id, team_no) VALUES (?, ?, 2)", id, u.getId());
        if (winningTeam != null) {
            jdbc.update("INSERT INTO match_results (match_id, winning_team) VALUES (?, ?)", id, winningTeam);
        }
        for (int i = 0; i < games.length; i++) {
            jdbc.update("INSERT INTO match_games (match_id, game_no, team1_score, team2_score) VALUES (?, ?, ?, ?)",
                    id, i + 1, games[i][0], games[i][1]);
        }
        return id;
    }

    private UUID standardBatch(double p1) {
        UUID batch = UUID.randomUUID();
        option(batch, 1, true, p1, 100, ids(w, x), ids(y, z), w);
        option(batch, 2, false, 50.0, 0, ids(w, y), ids(x, z), w);
        option(batch, 3, false, 40.0, 60, ids(w, z), ids(x, y), w);
        return batch;
    }

    @Test
    @DisplayName("Doi chieu dung cach chia doi (ke ca khi dao so doi 1/2): du doan, Brier, do chenh, chenh diem thuc te")
    void reconcilesWithPlayedMatch() throws Exception {
        UUID batch = standardBatch(64.0);
        // Tran that: doi 1 = (y,z), doi 2 = (w,x), doi 2 thang -> doi 1 cua phuong an (w,x) thang.
        UUID matchId = match("completed", "2020-03-12T18:00:00+07", 2, new User[] {y, z}, new User[] {w, x},
                new int[][] {{15, 21}, {19, 21}});

        JsonNode result = accuracy();
        assertThat(result.get("batches").asLong()).isEqualTo(1);
        assertThat(result.get("options").asLong()).isEqualTo(3);
        JsonNode all = result.get("overall");
        assertThat(all.get("reconciled").asLong()).isEqualTo(1);
        assertThat(all.get("evaluated").asLong()).isEqualTo(1);
        assertThat(all.get("correct").asLong()).isEqualTo(1);
        assertThat(all.get("accuracy").asDouble()).isEqualTo(100.0);
        assertThat(all.get("brier").asDouble()).isEqualTo(0.130);
        assertThat(all.get("avgBalanceGap").asDouble()).isEqualTo(100.0);
        assertThat(all.get("avgPointMargin").asDouble()).isEqualTo(8.0);
        assertThat(all.get("sufficient").asBoolean()).isFalse();
        assertThat(result.get("recommended").get("reconciled").asLong()).isEqualTo(1);
        assertThat(result.get("others").get("reconciled").asLong()).isZero();
        assertThat(result.get("others").has("accuracy")).isFalse();
        assertThat(result.get("recommendedChosenRate").asDouble()).isEqualTo(100.0);

        JsonNode sample = result.get("samples").get(0);
        assertThat(sample.get("matchId").asText()).isEqualTo(matchId.toString());
        assertThat(sample.get("batchId").asText()).isEqualTo(batch.toString());
        assertThat(sample.get("winningTeam").asInt()).isEqualTo(1);
        assertThat(sample.get("predictedWinningTeam").asInt()).isEqualTo(1);
        assertThat(sample.get("correct").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Du doan sai duoc dem la sai; phuong an khong khuyen nghi tinh rieng; xac suat 50% khong tinh la du doan")
    void countsWrongAndNeutralPredictions() throws Exception {
        standardBatch(30.0);
        // Hai phuong an cung duoc choi: (w,x|y,z) doi (w,x) thang va (w,y|x,z) doi (x,z) thang.
        match("completed", "2020-03-12T18:00:00+07", 1, new User[] {w, x}, new User[] {y, z}, new int[][] {{21, 10}});
        match("completed", "2020-03-13T18:00:00+07", 2, new User[] {w, y}, new User[] {x, z}, new int[][] {{10, 21}});

        JsonNode result = accuracy();
        JsonNode all = result.get("overall");
        assertThat(all.get("reconciled").asLong()).isEqualTo(2);
        // Phuong an 1: du doan doi 2 thang (30%) nhung doi 1 thang -> sai. Phuong an 2: 50% -> khong tinh du doan.
        assertThat(all.get("evaluated").asLong()).isEqualTo(1);
        assertThat(all.get("correct").asLong()).isZero();
        assertThat(all.get("accuracy").asDouble()).isEqualTo(0.0);
        // Brier = ((0.3-1)^2 + (0.5-0)^2)/2 = (0.49 + 0.25)/2 = 0.37 (phuong an 2: doi 1 (w,y) thua nen ket qua = 0)
        assertThat(all.get("brier").asDouble()).isEqualTo(0.37);
        assertThat(result.get("recommended").get("reconciled").asLong()).isEqualTo(1);
        assertThat(result.get("others").get("reconciled").asLong()).isEqualTo(1);
        assertThat(result.get("others").get("evaluated").asLong()).isZero();
        assertThat(result.get("recommendedChosenRate").asDouble()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Khong doi chieu: tran chua hoan thanh, chua co ket qua, khac cach chia, truoc de xuat, sau 30 ngay, hon 4 nguoi")
    void ignoresMatchesThatDoNotQualify() throws Exception {
        standardBatch(64.0);
        match("scheduled", "2020-03-12T18:00:00+07", null, new User[] {w, x}, new User[] {y, z}, new int[][] {});
        match("cancelled", "2020-03-12T18:00:00+07", 1, new User[] {w, x}, new User[] {y, z}, new int[][] {});
        match("completed", "2020-03-12T18:00:00+07", null, new User[] {w, x}, new User[] {y, z}, new int[][] {});
        match("completed", "2020-03-09T18:00:00+07", 1, new User[] {w, x}, new User[] {y, z}, new int[][] {});
        match("completed", "2020-04-20T18:00:00+07", 1, new User[] {w, x}, new User[] {y, z}, new int[][] {});
        User extra = persistUser("tacc-extra", Role.PLAYER);
        match("completed", "2020-03-12T18:00:00+07", 1, new User[] {w, x, extra}, new User[] {y, z}, new int[][] {});

        JsonNode result = accuracy();
        assertThat(result.get("batches").asLong()).isEqualTo(1);
        assertThat(result.get("overall").get("reconciled").asLong()).isZero();
        assertThat(result.get("overall").has("accuracy")).isFalse();
        assertThat(result.get("overall").has("brier")).isFalse();
        assertThat(result.has("recommendedChosenRate")).isFalse();
        assertThat(result.get("samples")).isEmpty();
    }

    @Test
    @DisplayName("Chi lay tran dau tien khi nhieu tran cung cach chia; loc nguon; bo du lieu mau; kiem tra quyen")
    void usesEarliestMatchAndFilters() throws Exception {
        UUID batch = standardBatch(64.0);
        UUID first = match("completed", "2020-03-12T18:00:00+07", 1, new User[] {w, x}, new User[] {y, z}, new int[][] {});
        match("completed", "2020-03-14T18:00:00+07", 2, new User[] {w, x}, new User[] {y, z}, new int[][] {});
        JsonNode result = accuracy();
        assertThat(result.get("overall").get("reconciled").asLong()).isEqualTo(1);
        assertThat(result.get("samples").get(0).get("matchId").asText()).isEqualTo(first.toString());
        assertThat(result.get("samples").get(0).get("batchId").asText()).isEqualTo(batch.toString());

        assertThat(accuracy("source", "ai").get("overall").get("reconciled").asLong()).isZero();
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("source", "x"))
                .andExpect(status().isBadRequest());

        jdbc.update("UPDATE users SET email = ? WHERE id = ?", "sample-" + UUID.randomUUID() + "@bwf.example", w.getId());
        assertThat(accuracy().get("batches").asLong()).isZero();

        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(x)))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }
}
