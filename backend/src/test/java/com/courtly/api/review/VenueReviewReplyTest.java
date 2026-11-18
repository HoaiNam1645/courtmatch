package com.courtly.api.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 2.3.81 - chu san xem danh gia san cua minh va tra loi cong khai. */
class VenueReviewReplyTest extends VenueReviewTestBase {

    private static final String OWNER_REVIEWS = "/api/v1/owner/venue-reviews";

    private JsonNode reply(String token, String reviewId, int expected, String content) throws Exception {
        String body = content == null ? "{}" : "{\"content\":\"" + content + "\"}";
        return objectMapper.readTree(mockMvc.perform(post(OWNER_REVIEWS + "/" + reviewId + "/reply")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode ownerList(String token, String query) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(OWNER_REVIEWS + query).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String write(String token, int rating, String comment) throws Exception {
        return review(token, completedBooking(token, 1), 201, body(rating, comment)).get("id").asText();
    }

    private JsonNode publicList() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/venues/" + venue.getSlug() + "/reviews"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Chu san tra loi: luu cau da cat khoang trang, hien ngay duoi danh gia o trang cong khai, bao cho nguoi danh gia")
    void ownerRepliesPublicly() throws Exception {
        String id = write(playerToken, 4, "Kha tot");
        JsonNode item = reply(ownerToken, id, 201, "  Cam on ban da ung ho!  ");
        assertThat(item.get("reply").get("content").asText()).isEqualTo("Cam on ban da ung ho!");
        assertThat(item.get("reply").get("edited").asBoolean()).isFalse();
        assertThat(item.get("venueName").asText()).isEqualTo(venue.getName());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venue_review_replies WHERE review_id = ?::uuid AND owner_id = ?",
                Integer.class, id, owner.getId())).isEqualTo(1);

        JsonNode shown = publicList().get("content").get(0);
        assertThat(shown.get("id").asText()).isEqualTo(id);
        assertThat(shown.get("reply").get("content").asText()).isEqualTo("Cam on ban da ung ho!");
        assertThat(shown.get("reply").get("repliedAt").asText()).isNotBlank();
        assertThat(shown.get("reply").has("ownerId")).isFalse();

        entityManager.flush();
        JsonNode notification = objectMapper.readTree(jdbc.queryForObject(
                "SELECT data_json::text FROM notifications WHERE user_id = ? AND type = 'venue_review_replied'", String.class, player.getId()));
        assertThat(notification.get("route").asText()).isEqualTo("/san/" + venue.getSlug() + "#danh-gia");
        assertThat(notification.get("reviewId").asText()).isEqualTo(id);
    }

    @Test
    @DisplayName("Moi danh gia chi co mot cau tra loi: tra loi lan hai bi 409 va giu nguyen cau dau")
    void onlyOneReplyPerReview() throws Exception {
        String id = write(playerToken, 5, null);
        reply(ownerToken, id, 201, "Cau dau");
        JsonNode again = reply(ownerToken, id, 409, "Cau hai");
        assertThat(again.get("code").asText()).isEqualTo("VENUE_REVIEW_STATE_INVALID");
        assertThat(jdbc.queryForList("SELECT content FROM venue_review_replies WHERE review_id = ?::uuid", String.class, id))
                .containsExactly("Cau dau");
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'venue_review_replied'",
                Integer.class, player.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Chi chu san cua san do tra loi duoc: chu san khac va id la tra 404, nguoi choi 403, chua dang nhap 401")
    void onlyTheVenueOwner() throws Exception {
        String id = write(playerToken, 5, null);
        String otherOwner = loginAs(persistOwner("other-owner"));
        reply(otherOwner, id, 404, "Khong phai san cua toi");
        reply(ownerToken, UUID.randomUUID().toString(), 404, "Khong co");
        mockMvc.perform(post(OWNER_REVIEWS + "/" + id + "/reply").header("Authorization", bearer(playerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post(OWNER_REVIEWS + "/" + id + "/reply").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venue_review_replies", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Chi tra loi danh gia dang hien thi; danh gia bi an hoac bao cao tra 409")
    void onlyVisibleReviews() throws Exception {
        String id = write(playerToken, 1, "Te");
        for (String state : new String[] {"hidden", "reported"}) {
            jdbc.update("UPDATE venue_reviews SET status = ? WHERE id = ?::uuid", state, id);
            assertThat(reply(ownerToken, id, 409, "Xin loi").get("code").asText()).isEqualTo("VENUE_REVIEW_STATE_INVALID");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venue_review_replies", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Noi dung tra loi: bat buoc, khong chi khoang trang, toi da 1000 ky tu")
    void validatesContent() throws Exception {
        String id = write(playerToken, 5, null);
        assertThat(reply(ownerToken, id, 400, null).get("fieldErrors").has("content")).isTrue();
        assertThat(reply(ownerToken, id, 400, "").get("fieldErrors").has("content")).isTrue();
        assertThat(reply(ownerToken, id, 400, "    ").get("fieldErrors").has("content")).isTrue();
        assertThat(reply(ownerToken, id, 400, "x".repeat(1001)).get("fieldErrors").has("content")).isTrue();
        reply(ownerToken, id, 201, "x".repeat(1000));
    }

    @Test
    @DisplayName("Tat thong bao loai nay thi van tra loi duoc nhung khong gui thong bao")
    void respectsNotificationPreference() throws Exception {
        String id = write(playerToken, 5, null);
        jdbc.update("INSERT INTO notification_preferences (user_id, type, in_app_enabled, email_enabled) VALUES (?, 'venue_review_replied', false, false)",
                player.getId());
        reply(ownerToken, id, 201, "Cam on");
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venue_review_replies", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'venue_review_replied'",
                Integer.class, player.getId())).isZero();
    }

    @Test
    @DisplayName("Danh sach cua chu san: chi danh gia hien thi cua san minh, loc chua/da tra loi, so chua tra loi khong phu thuoc bo loc")
    void ownerListFilters() throws Exception {
        String second = loginAs(persistUser("reviewer-2", Role.PLAYER));
        String third = loginAs(persistUser("reviewer-3", Role.PLAYER));
        String fourth = loginAs(persistUser("reviewer-4", Role.PLAYER));
        String answered = write(playerToken, 5, "Da tra loi");
        String open = write(second, 3, "Chua tra loi");
        String hidden = write(third, 1, "Bi an");
        write(fourth, 4, "Chua tra loi 2");
        jdbc.update("UPDATE venue_reviews SET status = 'hidden' WHERE id = ?::uuid", hidden);
        reply(ownerToken, answered, 201, "Cam on");

        JsonNode all = ownerList(ownerToken, "");
        assertThat(all.get("totalElements").asLong()).isEqualTo(3);
        assertThat(all.get("unanswered").asLong()).isEqualTo(2);
        JsonNode unanswered = ownerList(ownerToken, "?filter=unanswered");
        assertThat(unanswered.get("totalElements").asLong()).isEqualTo(2);
        unanswered.get("items").forEach(item -> assertThat(item.has("reply")).isFalse());
        assertThat(unanswered.get("unanswered").asLong()).isEqualTo(2);
        JsonNode done = ownerList(ownerToken, "?filter=answered");
        assertThat(done.get("totalElements").asLong()).isEqualTo(1);
        assertThat(done.get("items").get(0).get("id").asText()).isEqualTo(answered);
        assertThat(done.get("items").get(0).get("reviewerName").asText()).startsWith("Nguoi Dung reviewer");
        assertThat(ownerList(ownerToken, "?rating=3").get("items").get(0).get("id").asText()).isEqualTo(open);
        assertThat(ownerList(ownerToken, "?size=2&page=1").get("items")).hasSize(1);
        assertThat(ownerList(ownerToken, "?venueId=" + venue.getId()).get("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("Chu san khac khong thay danh gia cua san nay; venueId cua san khac tra 404; tham so sai bi tu choi")
    void ownerListIsolation() throws Exception {
        write(playerToken, 5, "Rieng cua san nay");
        User stranger = persistOwner("stranger-owner");
        String strangerToken = loginAs(stranger);
        JsonNode empty = ownerList(strangerToken, "");
        assertThat(empty.get("totalElements").asLong()).isZero();
        assertThat(empty.get("unanswered").asLong()).isZero();
        mockMvc.perform(get(OWNER_REVIEWS + "?venueId=" + venue.getId()).header("Authorization", bearer(strangerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(OWNER_REVIEWS + "?filter=weird").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(OWNER_REVIEWS + "?rating=9").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(OWNER_REVIEWS + "?size=51").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(OWNER_REVIEWS).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
    }

    private JsonNode editReply(String token, String reviewId, int expected, String content) throws Exception {
        String body = content == null ? "{}" : "{\"content\":\"" + content + "\"}";
        return objectMapper.readTree(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put(OWNER_REVIEWS + "/" + reviewId + "/reply")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("2.3.82 Sua cau tra loi: luu noi dung moi, hien nhan da chinh sua o trang cong khai va trang chu san, khong gui lai thong bao")
    void ownerEditsTheReply() throws Exception {
        String id = write(playerToken, 4, "Kha tot");
        reply(ownerToken, id, 201, "Cau goc");
        entityManager.flush();
        jdbc.update("UPDATE venue_review_replies SET created_at = created_at - interval '1 hour' WHERE review_id = ?::uuid", id);
        entityManager.clear();

        JsonNode edited = editReply(ownerToken, id, 200, "  Cau da sua  ");
        assertThat(edited.get("reply").get("content").asText()).isEqualTo("Cau da sua");
        assertThat(edited.get("reply").get("edited").asBoolean()).isTrue();
        entityManager.flush();
        assertThat(jdbc.queryForList("SELECT content FROM venue_review_replies WHERE review_id = ?::uuid", String.class, id))
                .containsExactly("Cau da sua");
        entityManager.clear();

        JsonNode shown = publicList().get("content").get(0);
        assertThat(shown.get("reply").get("content").asText()).isEqualTo("Cau da sua");
        assertThat(shown.get("reply").get("edited").asBoolean()).isTrue();
        assertThat(ownerList(ownerToken, "").get("items").get(0).get("reply").get("edited").asBoolean()).isTrue();
        // Chi co dung mot thong bao (luc tra loi lan dau), sua khong gui them.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'venue_review_replied'",
                Integer.class, player.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("2.3.82 Khong sua duoc khi chua co cau tra loi, danh gia khong hien thi, sai chu san, noi dung sai")
    void editReplyGuards() throws Exception {
        String id = write(playerToken, 4, "Kha tot");
        assertThat(editReply(ownerToken, id, 409, "Chua co cau nao").get("code").asText()).isEqualTo("VENUE_REVIEW_STATE_INVALID");
        reply(ownerToken, id, 201, "Cau goc");

        assertThat(editReply(ownerToken, id, 400, null).get("fieldErrors").has("content")).isTrue();
        assertThat(editReply(ownerToken, id, 400, "   ").get("fieldErrors").has("content")).isTrue();
        assertThat(editReply(ownerToken, id, 400, "x".repeat(1001)).get("fieldErrors").has("content")).isTrue();
        editReply(loginAs(persistOwner("other-edit-owner")), id, 404, "Khong phai san cua toi");
        editReply(ownerToken, UUID.randomUUID().toString(), 404, "Khong co");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(OWNER_REVIEWS + "/" + id + "/reply")
                .header("Authorization", bearer(playerToken)).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(OWNER_REVIEWS + "/" + id + "/reply")
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}")).andExpect(status().isUnauthorized());

        jdbc.update("UPDATE venue_reviews SET status = 'hidden' WHERE id = ?::uuid", id);
        entityManager.clear();
        assertThat(editReply(ownerToken, id, 409, "Sau khi an").get("code").asText()).isEqualTo("VENUE_REVIEW_STATE_INVALID");
        assertThat(jdbc.queryForObject("SELECT content FROM venue_review_replies WHERE review_id = ?::uuid", String.class, id)).isEqualTo("Cau goc");
    }

    @Test
    @DisplayName("Xoa danh gia thi cau tra loi xoa theo")
    void replyDisappearsWithTheReview() throws Exception {
        String id = write(playerToken, 5, null);
        reply(ownerToken, id, 201, "Cam on");
        entityManager.flush();
        entityManager.clear(); // moi request that co persistence context rieng; test dung chung mot giao dich
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/venue-reviews/" + id)
                .header("Authorization", bearer(playerToken))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venue_review_replies", Integer.class)).isZero();
    }
}
