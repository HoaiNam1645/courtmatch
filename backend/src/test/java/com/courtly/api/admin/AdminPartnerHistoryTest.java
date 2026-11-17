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

/** 2.3.75 - lich su ghep cap nguoi choi cho quan tri vien. */
class AdminPartnerHistoryTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/matching/partner-history";
    private static final String DAY = "2020-03-10";

    @Autowired private JdbcTemplate jdbc;

    private String adminToken;
    private User a;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        adminToken = loginAs(persistAdmin("hist-admin"));
        a = persistUser("hist-a", Role.PLAYER);
    }

    private JsonNode list(String... params) throws Exception {
        var request = get(PATH).header("Authorization", bearer(adminToken)).param("from", DAY).param("to", DAY);
        for (int i = 0; i < params.length; i += 2) request = request.param(params[i], params[i + 1]);
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private UUID suggest(User to, int rank, double score, String at) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO partner_suggestions (id, user_id, suggested_user_id, total_score, rank_no, scoring_source, created_at)
                VALUES (?, ?, ?, ?, ?, 'baseline', ?::timestamptz)
                """, id, a.getId(), to.getId(), score, rank, at);
        return id;
    }

    private void request(User to, String status, String at) {
        jdbc.update("INSERT INTO partner_requests (requester_id, receiver_id, status, created_at) VALUES (?, ?, ?, ?::timestamptz)",
                a.getId(), to.getId(), status, at);
    }

    private List<String> outcomes(JsonNode page) {
        List<String> result = new ArrayList<>();
        page.get("content").forEach(item -> result.add(item.get("outcome").asText()));
        return result;
    }

    /** Sau khi chay: gioi tinh dong theo thu tu thoi gian giam dan (nguoi cuoi cung duoc de xuat dau tien). */
    private void sixOutcomes() {
        String[] statuses = {"accepted", "rejected", null, "expired", "pending", "cancelled"};
        for (int i = 0; i < statuses.length; i++) {
            User to = persistUser("hist-to" + i, Role.PLAYER);
            String at = "%sT%02d:00:00+07".formatted(DAY, 8 + i);
            suggest(to, i + 1, 90 - i * 10, at);
            if (statuses[i] != null) request(to, statuses[i], "2020-03-11T09:00:00+07");
        }
    }

    @Test
    @DisplayName("Moi goi y kem ket qua yeu cau, moi nhat truoc; loc theo ket qua; phan trang")
    void listsSuggestionsWithOutcomes() throws Exception {
        sixOutcomes();
        JsonNode all = list();
        assertThat(all.get("totalElements").asLong()).isEqualTo(6);
        assertThat(outcomes(all)).containsExactly("cancelled", "pending", "expired", "none", "rejected", "accepted");
        JsonNode first = all.get("content").get(0);
        assertThat(first.get("viewer").get("email").asText()).isEqualTo(a.getEmail());
        assertThat(first.get("rankNo").asInt()).isEqualTo(6);
        assertThat(first.get("totalScore").asDouble()).isEqualTo(40.0);
        assertThat(first.get("scoringSource").asText()).isEqualTo("baseline");
        assertThat(first.get("requestId").asText()).isNotBlank();
        assertThat(all.get("content").get(3).has("requestId")).isFalse();

        assertThat(outcomes(list("outcome", "none"))).containsExactly("none");
        assertThat(outcomes(list("outcome", "accepted"))).containsExactly("accepted");
        assertThat(outcomes(list("outcome", "expired"))).containsExactly("expired");
        assertThat(list("outcome", "all").get("totalElements").asLong()).isEqualTo(6);

        JsonNode page = list("size", "2", "page", "1");
        assertThat(page.get("content")).hasSize(2);
        assertThat(page.get("totalElements").asLong()).isEqualTo(6);
        assertThat(page.get("totalPages").asInt()).isEqualTo(3);
        assertThat(outcomes(page)).containsExactly("expired", "none");
    }

    @Test
    @DisplayName("Tim theo ten/email nguoi xem hoac nguoi duoc goi y; loc nguon; khoang ngay")
    void filters() throws Exception {
        User target = persistUser("hist-target", Role.PLAYER);
        suggest(target, 1, 80, DAY + "T09:00:00+07");
        User other = persistUser("hist-other", Role.PLAYER);
        User stranger = persistUser("hist-stranger", Role.PLAYER);
        jdbc.update("INSERT INTO partner_suggestions (user_id, suggested_user_id, total_score, scoring_source, created_at) VALUES (?, ?, 10, 'baseline', ?::timestamptz)",
                other.getId(), stranger.getId(), DAY + "T09:00:00+07");

        assertThat(list().get("totalElements").asLong()).isEqualTo(2);
        assertThat(list("q", "hist-target").get("totalElements").asLong()).isEqualTo(1);
        assertThat(list("q", "HIST-TARGET").get("totalElements").asLong()).isEqualTo(1); // khong phan biet hoa thuong
        assertThat(list("q", a.getEmail()).get("totalElements").asLong()).isEqualTo(1); // nguoi xem
        assertThat(list("q", "hist-stranger").get("totalElements").asLong()).isEqualTo(1); // nguoi duoc goi y
        assertThat(list("q", "%").get("totalElements").asLong()).isZero(); // ky tu dac biet khong phai ky tu thay the
        assertThat(list("source", "baseline").get("totalElements").asLong()).isEqualTo(2);
        assertThat(list("source", "ai").get("totalElements").asLong()).isZero();
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("from", "2020-03-11").param("to", "2020-03-11"))
                .andExpect(status().isOk());
        assertThat(objectMapper.readTree(mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken))
                .param("from", "2020-03-11").param("to", "2020-03-11")).andReturn().getResponse().getContentAsString())
                .get("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("Yeu cau chi gan cho goi y moi nhat cua cung cap; yeu cau sau 7 ngay khong gan")
    void attributesRequestToLatestSuggestion() throws Exception {
        User b = persistUser("hist-latest", Role.PLAYER);
        suggest(b, 1, 70, DAY + "T08:00:00+07");
        suggest(b, 1, 70, DAY + "T20:00:00+07");
        request(b, "accepted", "2020-03-11T09:00:00+07");
        User late = persistUser("hist-slow", Role.PLAYER);
        suggest(late, 2, 60, DAY + "T09:00:00+07");
        request(late, "accepted", "2020-03-18T09:30:00+07");

        JsonNode result = list();
        assertThat(result.get("totalElements").asLong()).isEqualTo(3);
        assertThat(outcomes(list("q", "hist-latest"))).containsExactly("accepted", "none");
        assertThat(outcomes(list("q", "hist-slow"))).containsExactly("none");
    }

    @Test
    @DisplayName("Chi tiet: diem thanh phan, ly do, ket qua yeu cau va thong tin lan tim da sinh ra goi y")
    void showsDetail() throws Exception {
        User b = persistUser("hist-detail", Role.PLAYER);
        UUID run = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO matching_runs (id, run_type, user_id, scoring_source, result_count, duration_ms, status, params_json, created_at)
                VALUES (?, 'partner_search', ?, 'baseline', 7, 123, 'success', '{"durationMinutes": 90}'::jsonb, ?::timestamptz)
                """, run, a.getId(), DAY + "T09:00:00+07");
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO partner_suggestions (id, user_id, suggested_user_id, total_score, skill_score, location_score, time_score,
                       style_score, confidence_score, reason_json, search_id, rank_no, scoring_source, created_at)
                VALUES (?, ?, ?, 71.5, 90, 60, 70, 50, 0.8, '{"ratingGap": 20, "provisional": false}'::jsonb, ?, 3, 'baseline', ?::timestamptz)
                """, id, a.getId(), b.getId(), run, DAY + "T09:00:00+07");
        request(b, "rejected", "2020-03-11T09:00:00+07");

        JsonNode detail = objectMapper.readTree(mockMvc.perform(get(PATH + "/" + id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(detail.get("item").get("outcome").asText()).isEqualTo("rejected");
        assertThat(detail.get("item").get("suggested").get("fullName").asText()).isEqualTo(b.getFullName());
        assertThat(detail.get("skillScore").asDouble()).isEqualTo(90.0);
        assertThat(detail.get("locationScore").asDouble()).isEqualTo(60.0);
        assertThat(detail.get("timeScore").asDouble()).isEqualTo(70.0);
        assertThat(detail.get("styleScore").asDouble()).isEqualTo(50.0);
        assertThat(detail.get("confidenceScore").asDouble()).isEqualTo(0.8);
        assertThat(detail.get("reason").get("ratingGap").asInt()).isEqualTo(20);
        assertThat(detail.get("search").get("durationMs").asInt()).isEqualTo(123);
        assertThat(detail.get("search").get("resultCount").asInt()).isEqualTo(7);
        assertThat(detail.get("search").get("params").get("durationMinutes").asInt()).isEqualTo(90);

        UUID plain = suggest(persistUser("hist-plain", Role.PLAYER), 1, 50, DAY + "T09:00:00+07");
        JsonNode noRun = objectMapper.readTree(mockMvc.perform(get(PATH + "/" + plain).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(noRun.has("search")).isFalse();
        assertThat(noRun.get("item").get("outcome").asText()).isEqualTo("none");

        mockMvc.perform(get(PATH + "/" + UUID.randomUUID()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Du lieu mau bi bo; tham so sai bi tu choi; nguoi choi khong xem duoc")
    void validatesAndProtects() throws Exception {
        User b = persistUser("hist-sample", Role.PLAYER);
        UUID id = suggest(b, 1, 80, DAY + "T09:00:00+07");
        jdbc.update("UPDATE users SET email = ? WHERE id = ?", "sample-" + UUID.randomUUID() + "@bwf.example", b.getId());
        assertThat(list().get("totalElements").asLong()).isZero();
        mockMvc.perform(get(PATH + "/" + id).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());

        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("outcome", "weird")).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(a)))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH + "/" + id).header("Authorization", bearer(loginAs(a)))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }
}
