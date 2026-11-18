package com.courtly.api.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 2.3.84 - quan tri vien an hoac khoi phuc danh gia san bi chu san bao cao. */
class AdminVenueReviewReportTest extends VenueReviewTestBase {

    private static final String PATH = "/api/v1/admin/venue-review-reports";

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUpAdmin() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("vr-admin");
        adminToken = loginAs(admin);
    }

    private JsonNode call(String method, String path, int expected, String json) throws Exception {
        var request = "GET".equals(method) ? get(PATH + path) : post(PATH + path);
        request = request.header("Authorization", bearer(adminToken));
        if (json != null) request = request.contentType(MediaType.APPLICATION_JSON).content(json);
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private static String note(String text) {
        return "{\"note\":\"" + text + "\"}";
    }

    private String write(String token, int rating, String comment) throws Exception {
        return review(token, completedBooking(token, 1), 201, body(rating, comment)).get("id").asText();
    }

    /** Chu san bao cao qua API; tra ve id bao cao. */
    private String ownerReports(String reviewId, String reason, String description) throws Exception {
        mockMvc.perform(post("/api/v1/owner/venue-reviews/" + reviewId + "/report").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + reason + "\",\"description\":" + (description == null ? "null" : "\"" + description + "\"") + "}"))
                .andExpect(status().isCreated());
        entityManager.flush();
        entityManager.clear();
        return jdbc.queryForObject("SELECT id::text FROM reports WHERE target_id = ?::uuid ORDER BY created_at DESC LIMIT 1", String.class, reviewId);
    }

    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Danh sach: bao cao cua cac danh gia san kem noi dung, ten nguoi viet, ten san, nhan ly do; dem theo trang thai; chua xu ly len dau")
    void listsReports() throws Exception {
        String a = write(playerToken, 1, "Danh gia xuc pham so mot");
        String second = loginAs(persistUser("vr-second", Role.PLAYER));
        String b = write(second, 2, "Danh gia vu khong so hai");
        String reportA = ownerReports(a, "offensive", null);
        String reportB = ownerReports(b, "defamation", "Noi sai ve gia san");
        jdbc.update("UPDATE reports SET status = 'rejected', handling_action = 'review_kept' WHERE id = ?::uuid", reportB);
        sync();

        JsonNode all = call("GET", "?size=100", 200, null);
        assertThat(all.get("statusCounts").get("pending").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(all.get("statusCounts").get("rejected").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(all.get("reasons")).hasSize(5);
        JsonNode first = null;
        JsonNode other = null;
        for (JsonNode item : all.get("content")) {
            if (item.get("id").asText().equals(reportA)) first = item;
            if (item.get("id").asText().equals(reportB)) other = item;
        }
        assertThat(first).isNotNull();
        assertThat(first.get("status").asText()).isEqualTo("pending");
        assertThat(first.get("reasonLabel").asText()).isEqualTo("Xúc phạm, lăng mạ");
        assertThat(first.get("reporterName").asText()).startsWith("Nguoi Dung owner-review");
        assertThat(first.get("review").get("comment").asText()).isEqualTo("Danh gia xuc pham so mot");
        assertThat(first.get("review").get("reviewerName").asText()).startsWith("Nguoi Dung reviewer");
        assertThat(first.get("review").get("venueName").asText()).isEqualTo(venue.getName());
        assertThat(first.get("review").get("status").asText()).isEqualTo("visible");
        assertThat(other.get("status").asText()).isEqualTo("rejected");

        JsonNode pending = call("GET", "?status=pending&q=so mot", 200, null);
        assertThat(pending.get("content")).hasSize(1);
        assertThat(pending.get("content").get(0).get("id").asText()).isEqualTo(reportA);
        assertThat(call("GET", "?reason=defamation&q=vu khong", 200, null).get("content")).hasSize(1);
        assertThat(call("GET", "?q=" + venue.getName().replace(" ", " "), 200, null).get("totalElements").asLong()).isEqualTo(2);
        assertThat(call("GET", "?q=Nguoi Dung vr-second", 200, null).get("totalElements").asLong()).isEqualTo(1);
        assertThat(call("GET", "?q=khong-co-gi-ca-xyz", 200, null).get("content")).isEmpty();
        assertThat(call("GET", "?reviewStatus=hidden&q=so mot", 200, null).get("content")).isEmpty();
        assertThat(call("GET", "?reviewStatus=visible&q=so mot", 200, null).get("content")).hasSize(1);
        assertThat(call("GET", "?size=1&q=Danh gia", 200, null).get("totalPages").asInt()).isEqualTo(2);
        call("GET", "?status=weird", 400, null);
        call("GET", "?reviewStatus=weird", 400, null);
        call("GET", "?size=101", 400, null);
    }

    @Test
    @DisplayName("Chi tiet: danh gia day du, cau tra loi cua chu san, bao cao khac cung nham vao danh gia, co the nhan xu ly")
    void showsDetail() throws Exception {
        String id = write(playerToken, 1, "Noi dung day du cua danh gia");
        mockMvc.perform(post("/api/v1/owner/venue-reviews/" + id + "/reply").header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Cau tra loi cua chu san\"}")).andExpect(status().isCreated());
        String report = ownerReports(id, "other", "Mo ta chi tiet ly do khac nhau");
        jdbc.update("UPDATE reports SET status = 'rejected' WHERE id = ?::uuid", report);
        String latest = ownerReports(id, "spam", "Noi dung quang cao");
        sync();

        JsonNode detail = call("GET", "/" + latest, 200, null);
        assertThat(detail.get("status").asText()).isEqualTo("pending");
        assertThat(detail.get("reason").asText()).isEqualTo("spam");
        assertThat(detail.get("description").asText()).isEqualTo("Noi dung quang cao");
        assertThat(detail.get("review").get("comment").asText()).isEqualTo("Noi dung day du cua danh gia");
        assertThat(detail.get("review").get("reply").get("content").asText()).isEqualTo("Cau tra loi cua chu san");
        assertThat(detail.get("totalReports").asLong()).isEqualTo(2);
        assertThat(detail.get("related")).hasSize(1);
        assertThat(detail.get("related").get(0).get("id").asText()).isEqualTo(report);
        assertThat(detail.get("canStart").asBoolean()).isTrue();
        assertThat(detail.get("canConclude").asBoolean()).isFalse();
        assertThat(detail.get("involved").asBoolean()).isFalse();
        assertThat(detail.toString()).doesNotContain("@example.com");
        call("GET", "/" + UUID.randomUUID(), 404, null);
    }

    @Test
    @DisplayName("Nhan xu ly: pending -> reviewing, ghi audit; khong nhan lai; phai nhan truoc khi ket luan")
    void startsReport() throws Exception {
        String report = ownerReports(write(playerToken, 1, "Te"), "offensive", null);
        call("POST", "/" + report + "/hide", 409, note("An ngay khi chua nhan"));
        assertThat(call("POST", "/" + report + "/start", 200, null).get("status").asText()).isEqualTo("reviewing");
        sync();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'report.start' AND entity_id = ?::uuid AND actor_id = ?",
                Integer.class, report, admin.getId())).isEqualTo(1);
        assertThat(call("POST", "/" + report + "/start", 409, null).get("code").asText()).isEqualTo("REPORT_STATE_INVALID");
    }

    @Test
    @DisplayName("An danh gia: ghi chu bat buoc, danh gia hidden, bao cao resolved, diem trung binh va so danh gia cua san tinh lai, nguoi viet duoc bao")
    void hidesTheReview() throws Exception {
        String keep = write(loginAs(persistUser("vr-keeper", Role.PLAYER)), 5, "Rat tot");
        String bad = write(playerToken, 1, "Xuc pham chu san");
        assertThat(venueRating()).isEqualTo(3.0);
        assertThat(venueReviewCount()).isEqualTo(2);
        String report = ownerReports(bad, "offensive", null);
        call("POST", "/" + report + "/start", 200, null);

        call("POST", "/" + report + "/hide", 400, "{\"note\":\"\"}");
        call("POST", "/" + report + "/hide", 400, note("ab"));
        JsonNode hidden = call("POST", "/" + report + "/hide", 200, note("Loi le xuc pham ro rang"));
        assertThat(hidden.get("status").asText()).isEqualTo("resolved");
        assertThat(hidden.get("handling").get("action").asText()).isEqualTo("review_hidden");
        assertThat(hidden.get("handling").get("note").asText()).isEqualTo("Loi le xuc pham ro rang");
        assertThat(hidden.get("review").get("status").asText()).isEqualTo("hidden");
        assertThat(hidden.get("canRestore").asBoolean()).isTrue();
        assertThat(hidden.get("canHide").asBoolean()).isFalse();
        sync();

        assertThat(jdbc.queryForObject("SELECT status FROM venue_reviews WHERE id = ?::uuid", String.class, bad)).isEqualTo("hidden");
        assertThat(venueRating()).isEqualTo(5.0);
        assertThat(venueReviewCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'review.hide' AND entity_id = ?::uuid", Integer.class, bad)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'report.resolve' AND entity_id = ?::uuid", Integer.class, report)).isEqualTo(1);
        JsonNode notification = objectMapper.readTree(jdbc.queryForObject(
                "SELECT data_json::text FROM notifications WHERE user_id = ? AND type = 'venue_review_hidden'", String.class, player.getId()));
        assertThat(notification.get("reviewId").asText()).isEqualTo(bad);
        assertThat(notification.get("route").asText()).isEqualTo("/bookings");
        JsonNode publicList = objectMapper.readTree(mockMvc.perform(get("/api/v1/venues/" + venue.getSlug() + "/reviews"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(publicList.get("totalElements").asLong()).isEqualTo(1);
        assertThat(publicList.get("content").get(0).get("id").asText()).isEqualTo(keep);
        call("POST", "/" + report + "/hide", 409, note("Ket luan lai"));
    }

    @Test
    @DisplayName("Giu nguyen danh gia: bao cao rejected, danh gia va diem san khong doi, khong gui thong bao an")
    void keepsTheReview() throws Exception {
        String id = write(playerToken, 2, "Chua chac vi pham");
        String report = ownerReports(id, "defamation", null);
        call("POST", "/" + report + "/start", 200, null);
        JsonNode kept = call("POST", "/" + report + "/keep", 200, note("Khong vi pham quy dinh"));
        assertThat(kept.get("status").asText()).isEqualTo("rejected");
        assertThat(kept.get("handling").get("action").asText()).isEqualTo("review_kept");
        assertThat(kept.get("review").get("status").asText()).isEqualTo("visible");
        assertThat(kept.get("canRestore").asBoolean()).isFalse();
        sync();
        assertThat(venueRating()).isEqualTo(2.0);
        assertThat(venueReviewCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'venue_review_hidden'",
                Integer.class, player.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'report.reject' AND entity_id = ?::uuid", Integer.class, report)).isEqualTo(1);
        // Khong con khoa: nguoi viet sua lai duoc.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/venue-reviews/" + id)
                .header("Authorization", bearer(playerToken)).contentType(MediaType.APPLICATION_JSON).content(body(4, "Sua lai")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Khoi phuc danh gia da an: hien lai, diem san tinh lai; bao cao van la resolved; danh gia dang hien thi thi 409")
    void restoresAHiddenReview() throws Exception {
        write(loginAs(persistUser("vr-other-rating", Role.PLAYER)), 5, "Tot");
        String bad = write(playerToken, 1, "Te");
        String report = ownerReports(bad, "offensive", null);
        call("POST", "/" + report + "/start", 200, null);
        call("POST", "/" + report + "/restore", 409, note("Dang hien thi, khong can khoi phuc"));
        call("POST", "/" + report + "/hide", 200, note("An truoc"));
        sync();
        assertThat(venueRating()).isEqualTo(5.0);

        call("POST", "/" + report + "/restore", 400, "{\"note\":\"\"}");
        JsonNode restored = call("POST", "/" + report + "/restore", 200, note("Xem lai thay khong vi pham"));
        assertThat(restored.get("review").get("status").asText()).isEqualTo("visible");
        assertThat(restored.get("status").asText()).isEqualTo("resolved");
        assertThat(restored.get("canRestore").asBoolean()).isFalse();
        sync();
        assertThat(venueRating()).isEqualTo(3.0);
        assertThat(venueReviewCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'review.restore' AND entity_id = ?::uuid", Integer.class, bad)).isEqualTo(1);
        call("POST", "/" + report + "/restore", 409, note("Lan hai"));
    }

    @Test
    @DisplayName("Danh gia con o trang thai reported cu: giu nguyen thi tro lai visible va tinh lai diem san")
    void keepRevivesLegacyReportedReview() throws Exception {
        String id = write(playerToken, 4, "Du lieu cu");
        String report = ownerReports(id, "spam", null);
        jdbc.update("UPDATE venue_reviews SET status = 'reported' WHERE id = ?::uuid", id);
        jdbc.update("UPDATE venues SET average_rating = 0, review_count = 0 WHERE id = ?", venue.getId());
        sync();
        call("POST", "/" + report + "/start", 200, null);
        call("POST", "/" + report + "/keep", 200, note("Giu lai"));
        sync();
        assertThat(jdbc.queryForObject("SELECT status FROM venue_reviews WHERE id = ?::uuid", String.class, id)).isEqualTo("visible");
        assertThat(venueRating()).isEqualTo(4.0);
        assertThat(venueReviewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Quan tri vien la nguoi viet danh gia khong tu xu ly bao cao ve danh gia cua minh")
    void adminInvolvedCannotHandle() throws Exception {
        UUID reviewId = UUID.randomUUID();
        jdbc.update("INSERT INTO venue_reviews (id, venue_id, user_id, rating, comment) VALUES (?, ?, ?, 1, 'Cua admin')",
                reviewId, venue.getId(), admin.getId());
        UUID reportId = UUID.randomUUID();
        jdbc.update("INSERT INTO reports (id, reporter_id, target_type, target_id, reason, status) VALUES (?, ?, 'venue_review', ?, 'spam', 'pending')",
                reportId, owner.getId(), reviewId);
        sync();
        JsonNode detail = call("GET", "/" + reportId, 200, null);
        assertThat(detail.get("involved").asBoolean()).isTrue();
        assertThat(detail.get("canStart").asBoolean()).isFalse();
        call("POST", "/" + reportId + "/start", 409, null);
    }

    @Test
    @DisplayName("Chi quan tri vien duoc dung: nguoi choi, chu san 403, chua dang nhap 401; bao cao khac loai tra 404")
    void accessRules() throws Exception {
        String report = ownerReports(write(playerToken, 1, "Te"), "offensive", null);
        for (String token : new String[] {playerToken, ownerToken}) {
            mockMvc.perform(get(PATH).header("Authorization", bearer(token))).andExpect(status().isForbidden());
            mockMvc.perform(post(PATH + "/" + report + "/start").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        UUID partnerReport = UUID.randomUUID();
        jdbc.update("INSERT INTO reports (id, reporter_id, target_type, target_id, reason) VALUES (?, ?, 'partner_review', ?, 'khac')",
                partnerReport, owner.getId(), UUID.randomUUID());
        call("GET", "/" + partnerReport, 404, null);
        call("POST", "/" + partnerReport + "/start", 404, null);
    }
}
