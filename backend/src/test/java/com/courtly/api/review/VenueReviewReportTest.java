package com.courtly.api.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 2.3.83 - chu san bao cao danh gia san co noi dung khong phu hop. */
class VenueReviewReportTest extends VenueReviewTestBase {

    private static final String OWNER_REVIEWS = "/api/v1/owner/venue-reviews";

    private JsonNode report(String token, String reviewId, int expected, String json) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(OWNER_REVIEWS + "/" + reviewId + "/report")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private static String body(String reason, String description) {
        return "{\"reason\":" + (reason == null ? "null" : "\"" + reason + "\"")
                + ",\"description\":" + (description == null ? "null" : "\"" + description + "\"") + "}";
    }

    private String write(String token, int rating, String comment) throws Exception {
        return review(token, completedBooking(token, 1), 201, body(rating, comment)).get("id").asText();
    }

    private JsonNode publicList(String token) throws Exception {
        var request = get("/api/v1/venues/" + venue.getSlug() + "/reviews");
        if (token != null) request = request.header("Authorization", bearer(token));
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode ownerItem(String reviewId) throws Exception {
        JsonNode list = objectMapper.readTree(mockMvc.perform(get(OWNER_REVIEWS).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode item : list.get("items")) if (item.get("id").asText().equals(reviewId)) return item;
        throw new AssertionError("review missing from owner list");
    }

    @Test
    @DisplayName("Danh muc ly do: 5 ly do co nhan va goi y, chi 'ly do khac' bat buoc mo ta")
    void listsTheReasonCatalog() throws Exception {
        JsonNode reasons = objectMapper.readTree(mockMvc.perform(get(OWNER_REVIEWS + "/report-reasons").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(reasons).hasSize(5);
        assertThat(reasons.get(0).get("code").asText()).isEqualTo("offensive");
        reasons.forEach(reason -> {
            assertThat(reason.get("label").asText()).isNotBlank();
            assertThat(reason.get("hint").asText()).isNotBlank();
            assertThat(reason.get("descriptionRequired").asBoolean()).isEqualTo(reason.get("code").asText().equals("other"));
        });
        mockMvc.perform(get(OWNER_REVIEWS + "/report-reasons").header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Bao cao luu o reports (venue_review, pending); danh gia van hien thi cong khai; chu san thay trang thai bao cao")
    void ownerReportsAReview() throws Exception {
        String id = write(playerToken, 1, "Qua te va xuc pham");
        JsonNode item = report(ownerToken, id, 201, body("defamation", "  Noi sai su that ve gia san  "));
        assertThat(item.get("id").asText()).isEqualTo(id);
        assertThat(item.get("report").get("status").asText()).isEqualTo("pending");
        assertThat(item.get("report").get("reason").asText()).isEqualTo("defamation");
        assertThat(item.get("report").get("reasonLabel").asText()).isEqualTo("Vu khống, sai sự thật");

        entityManager.flush();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM reports WHERE target_type = 'venue_review' AND target_id = ?::uuid AND reporter_id = ?
                  AND status = 'pending' AND reason = 'defamation' AND description = 'Noi sai su that ve gia san'
                """, Integer.class, id, owner.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM venue_reviews WHERE id = ?::uuid", String.class, id)).isEqualTo("visible");
        entityManager.clear();

        // Van hien thi cong khai, va van tinh vao diem trung binh cua san.
        assertThat(publicList(null).get("content").get(0).get("id").asText()).isEqualTo(id);
        assertThat(venueReviewCount()).isEqualTo(1);
        assertThat(ownerItem(id).get("report").get("status").asText()).isEqualTo("pending");
    }

    @Test
    @DisplayName("Moi chu san chi co mot bao cao dang cho cho moi danh gia; sau khi admin ket luan thi bao cao lai duoc")
    void oneOpenReportPerReview() throws Exception {
        String id = write(playerToken, 1, "Te");
        report(ownerToken, id, 201, body("offensive", null));
        assertThat(report(ownerToken, id, 409, body("spam", null)).get("code").asText()).isEqualTo("VENUE_REVIEW_STATE_INVALID");
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE target_id = ?::uuid", Integer.class, id)).isEqualTo(1);

        jdbc.update("UPDATE reports SET status = 'reviewing' WHERE target_id = ?::uuid", id);
        entityManager.clear();
        report(ownerToken, id, 409, body("spam", null));
        jdbc.update("UPDATE reports SET status = 'rejected', handling_action = 'review_kept', handled_at = now() WHERE target_id = ?::uuid", id);
        entityManager.clear();
        JsonNode kept = ownerItem(id);
        assertThat(kept.get("report").get("status").asText()).isEqualTo("rejected");
        assertThat(kept.get("report").get("handlingAction").asText()).isEqualTo("review_kept");
        report(ownerToken, id, 201, body("spam", "Bao cao lan hai"));
    }

    @Test
    @DisplayName("Ly do phai thuoc danh muc, 'ly do khac' bat buoc mo ta tu 10 ky tu, mo ta toi da 1000 ky tu")
    void validatesTheReport() throws Exception {
        String id = write(playerToken, 1, "Te");
        assertThat(report(ownerToken, id, 400, body(null, null)).get("fieldErrors").has("reason")).isTrue();
        assertThat(report(ownerToken, id, 400, body("", null)).get("fieldErrors").has("reason")).isTrue();
        assertThat(report(ownerToken, id, 400, body("not_in_catalog", null)).get("fieldErrors").has("reason")).isTrue();
        assertThat(report(ownerToken, id, 400, body("other", null)).get("fieldErrors").has("description")).isTrue();
        assertThat(report(ownerToken, id, 400, body("other", "ngan qua")).get("fieldErrors").has("description")).isTrue();
        assertThat(report(ownerToken, id, 400, body("other", "         ")).get("fieldErrors").has("description")).isTrue();
        assertThat(report(ownerToken, id, 400, body("offensive", "x".repeat(1001))).get("fieldErrors").has("description")).isTrue();
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE reporter_id = ?", Integer.class, owner.getId())).isZero();
        report(ownerToken, id, 201, body("other", "Noi dung khong lien quan den san"));
    }

    @Test
    @DisplayName("Chi chu san cua san do bao cao duoc (chu san khac 404, nguoi choi 403, chua dang nhap 401); danh gia bi an 409")
    void accessRules() throws Exception {
        String id = write(playerToken, 1, "Te");
        report(loginAs(persistOwner("other-report-owner")), id, 404, body("offensive", null));
        report(ownerToken, UUID.randomUUID().toString(), 404, body("offensive", null));
        mockMvc.perform(post(OWNER_REVIEWS + "/" + id + "/report").header("Authorization", bearer(playerToken))
                .contentType(MediaType.APPLICATION_JSON).content(body("offensive", null))).andExpect(status().isForbidden());
        mockMvc.perform(post(OWNER_REVIEWS + "/" + id + "/report").contentType(MediaType.APPLICATION_JSON).content(body("offensive", null)))
                .andExpect(status().isUnauthorized());

        jdbc.update("UPDATE venue_reviews SET status = 'hidden' WHERE id = ?::uuid", id);
        entityManager.clear();
        assertThat(report(ownerToken, id, 409, body("offensive", null)).get("code").asText()).isEqualTo("VENUE_REVIEW_STATE_INVALID");
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reports WHERE reporter_id = ?", Integer.class, owner.getId())).isZero();
    }

    @Test
    @DisplayName("Bo loc 'da bao cao' cua chu san: gom ca danh gia da bi an theo bao cao de xem ket qua; danh gia khong bao cao thi khong co")
    void reportedFilterShowsOutcomes() throws Exception {
        String other = loginAs(persistUser("reported-filter", Role.PLAYER));
        String hidden = write(playerToken, 1, "Se bi an");
        String kept = write(other, 2, "Duoc giu");
        String untouched = write(loginAs(persistUser("reported-untouched", Role.PLAYER)), 5, "Khong bao cao");
        report(ownerToken, hidden, 201, body("offensive", null));
        report(ownerToken, kept, 201, body("spam", null));
        entityManager.flush();
        jdbc.update("UPDATE reports SET status = 'resolved', handling_action = 'review_hidden' WHERE target_id = ?::uuid", hidden);
        jdbc.update("UPDATE venue_reviews SET status = 'hidden' WHERE id = ?::uuid", hidden);
        jdbc.update("UPDATE reports SET status = 'rejected', handling_action = 'review_kept' WHERE target_id = ?::uuid", kept);
        entityManager.clear();

        JsonNode reported = objectMapper.readTree(mockMvc.perform(get(OWNER_REVIEWS + "?filter=reported").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(reported.get("totalElements").asLong()).isEqualTo(2);
        java.util.Map<String, JsonNode> byId = new java.util.HashMap<>();
        reported.get("items").forEach(item -> byId.put(item.get("id").asText(), item));
        assertThat(byId).containsOnlyKeys(hidden, kept);
        assertThat(byId.get(hidden).get("status").asText()).isEqualTo("hidden");
        assertThat(byId.get(hidden).get("report").get("handlingAction").asText()).isEqualTo("review_hidden");
        assertThat(byId.get(kept).get("status").asText()).isEqualTo("visible");
        assertThat(byId.get(kept).get("report").get("handlingAction").asText()).isEqualTo("review_kept");
        assertThat(byId).doesNotContainKey(untouched);
        // Bo loc 'tat ca' chi con danh gia hien thi: danh gia da an khong lan vao.
        JsonNode all = objectMapper.readTree(mockMvc.perform(get(OWNER_REVIEWS).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        all.get("items").forEach(item -> assertThat(item.get("id").asText()).isNotEqualTo(hidden));
        // Chu san khac khong thay bao cao cua chu san nay.
        JsonNode stranger = objectMapper.readTree(mockMvc.perform(get(OWNER_REVIEWS + "?filter=reported")
                        .header("Authorization", bearer(loginAs(persistOwner("reported-stranger")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(stranger.get("totalElements").asLong()).isZero();
    }

    @Test
    @DisplayName("Co bao cao dang cho thi nguoi viet khong sua/xoa duoc (409), lich su dat san va trang san bao locked; het cho thi sua duoc lai")
    void reportedReviewIsLockedForItsAuthor() throws Exception {
        String id = write(playerToken, 2, "Danh gia can khoa");
        String stranger = loginAs(persistUser("viewer-lock", Role.PLAYER));
        report(ownerToken, id, 201, body("offensive", null));
        entityManager.flush();
        entityManager.clear();

        JsonNode edit = objectMapper.readTree(mockMvc.perform(put("/api/v1/venue-reviews/" + id).header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(VenueReviewTestBase.body(5, "Doi y")))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString());
        assertThat(edit.get("message").asText()).contains("bao cao");
        mockMvc.perform(delete("/api/v1/venue-reviews/" + id).header("Authorization", bearer(playerToken))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT comment FROM venue_reviews WHERE id = ?::uuid", String.class, id)).isEqualTo("Danh gia can khoa");

        // Chi nguoi viet thay locked = true; nguoi khac (va khach) khong biet danh gia co bi bao cao hay khong.
        assertThat(publicList(playerToken).get("content").get(0).get("locked").asBoolean()).isTrue();
        assertThat(publicList(stranger).get("content").get(0).get("locked").asBoolean()).isFalse();
        assertThat(publicList(null).get("content").get(0).get("locked").asBoolean()).isFalse();
        JsonNode history = objectMapper.readTree(mockMvc.perform(get("/api/v1/bookings").header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(history.get("content").get(0).get("review").get("locked").asBoolean()).isTrue();

        jdbc.update("UPDATE reports SET status = 'rejected' WHERE target_id = ?::uuid", id);
        entityManager.clear();
        assertThat(publicList(playerToken).get("content").get(0).get("locked").asBoolean()).isFalse();
        mockMvc.perform(put("/api/v1/venue-reviews/" + id).header("Authorization", bearer(playerToken))
                .contentType(MediaType.APPLICATION_JSON).content(VenueReviewTestBase.body(4, "Sua duoc roi"))).andExpect(status().isOk());
    }
}
