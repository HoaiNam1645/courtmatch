package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 2.3.91 - bo sung thong tin cho bao cao dang xu ly. */
class BookingDisputeSupplementTest extends BookingDisputeTestBase {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final String CONTENT = "Toi co them tin nhan chung minh chu san da hen mo cong";

    private UUID dispute() throws Exception {
        UUID bookingId = booking(playerToken, BookingStatus.COMPLETED, 1);
        return UUID.fromString(dispute(playerToken, bookingId, 201, "venue_closed", "San dong cua khi toi den luc 18h, khong ai mo").get("id").asText());
    }

    private JsonNode supplement(String token, UUID id, int expected, String content) throws Exception {
        String body = "{\"content\":" + (content == null ? "null" : "\"" + content + "\"") + "}";
        MockHttpServletRequestBuilder request = post("/api/v1/booking-disputes/" + id + "/supplements")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) request = request.header("Authorization", bearer(token));
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode image(String token, UUID id, String supplementId, int expected) throws Exception {
        var builder = multipart("/api/v1/booking-disputes/" + id + "/evidences" + (supplementId == null ? "" : "?supplementId=" + supplementId))
                .file(new MockMultipartFile("file", "bo-sung.png", "image/png", PNG));
        return objectMapper.readTree(mockMvc.perform(builder.header("Authorization", bearer(token)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode detail(UUID id) throws Exception {
        entityManager.flush();
        entityManager.clear();
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/booking-disputes/" + id).header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Bo sung khi cho chu san giai trinh: 201, luu vao reporter_updates_json, hien trong dong thoi gian, bao cho chu san")
    void addsASupplementAndNotifiesTheOwner() throws Exception {
        UUID id = dispute();
        JsonNode created = supplement(playerToken, id, 201, "  " + CONTENT + "  ");
        assertThat(created.get("content").asText()).isEqualTo(CONTENT);
        assertThat(created.get("supplementsRemaining").asInt()).isEqualTo(9);
        assertThat(created.get("evidenceRemaining").asInt()).isEqualTo(10);

        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(reporter_updates_json) FROM booking_disputes WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reporter_updates_json -> 0 ->> 'content' FROM booking_disputes WHERE id = ?", String.class, id)).isEqualTo(CONTENT);
        JsonNode data = objectMapper.readTree(jdbc.queryForObject(
                "SELECT data_json::text FROM notifications WHERE user_id = ? AND type = 'booking_dispute_updated'", String.class, owner.getId()));
        assertThat(data.get("disputeId").asText()).isEqualTo(id.toString());
        assertThat(data.get("route").asText()).isEqualTo("/owner/bookings");

        JsonNode timeline = detail(id).get("timeline");
        assertThat(timeline).hasSize(2);
        assertThat(timeline.get(1).get("type").asText()).isEqualTo("supplement");
        assertThat(timeline.get(1).get("text").asText()).isEqualTo(CONTENT);
        assertThat(detail(id).get("supplementsRemaining").asInt()).isEqualTo(9);
    }

    @Test
    @DisplayName("Dang duoc quan tri vien xem xet thi van bo sung duoc nhung khong gui thong bao cho chu san; da dong thi 409")
    void allowedWhileReviewingButNotWhenClosed() throws Exception {
        UUID id = dispute();
        jdbc.update("UPDATE booking_disputes SET status = 'reviewing', owner_response = 'Da giai trinh' WHERE id = ?", id);
        entityManager.clear();
        supplement(playerToken, id, 201, CONTENT);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'booking_dispute_updated'",
                Integer.class, owner.getId())).isZero();

        for (String closed : new String[] {"resolved", "rejected", "cancelled"}) {
            jdbc.update("UPDATE booking_disputes SET status = ? WHERE id = ?", closed, id);
            entityManager.clear();
            assertThat(supplement(playerToken, id, 409, CONTENT).get("code").asText()).isEqualTo("BOOKING_DISPUTE_INVALID");
        }
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(reporter_updates_json) FROM booking_disputes WHERE id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("Toi da 10 lan bo sung; lan thu 11 bi 409 va khong luu")
    void limitsTheNumberOfSupplements() throws Exception {
        UUID id = dispute();
        for (int i = 1; i <= 10; i++) {
            assertThat(supplement(playerToken, id, 201, CONTENT + " lan " + i).get("supplementsRemaining").asInt()).isEqualTo(10 - i);
        }
        assertThat(supplement(playerToken, id, 409, CONTENT + " lan 11").get("code").asText()).isEqualTo("BOOKING_DISPUTE_INVALID");
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(reporter_updates_json) FROM booking_disputes WHERE id = ?", Integer.class, id)).isEqualTo(10);
        assertThat(detail(id).get("canSupplement").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Noi dung 5-2000 ky tu, khong chi khoang trang")
    void validatesTheContent() throws Exception {
        UUID id = dispute();
        for (String bad : new String[] {null, "", "    ", "ngan", "  abcd  "}) {
            assertThat(supplement(playerToken, id, 400, bad).get("fieldErrors").has("content")).as(String.valueOf(bad)).isTrue();
        }
        assertThat(supplement(playerToken, id, 400, "x".repeat(2001)).get("fieldErrors").has("content")).isTrue();
        supplement(playerToken, id, 201, "x".repeat(2000));
        supplement(playerToken, id, 201, "nam ky");
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(reporter_updates_json) FROM booking_disputes WHERE id = ?", Integer.class, id)).isEqualTo(2);
    }

    @Test
    @DisplayName("Chi nguoi bao cao bo sung duoc (nguoi khac, chu san, id la 404; chua dang nhap 401); noi dung da gui khong sua/xoa duoc")
    void onlyTheReporterAndNoEditing() throws Exception {
        UUID id = dispute();
        supplement(loginAs(persistUser("supplement-stranger", Role.PLAYER)), id, 404, CONTENT);
        supplement(ownerToken, id, 404, CONTENT);
        supplement(playerToken, UUID.randomUUID(), 404, CONTENT);
        supplement(null, id, 401, CONTENT);
        String supplementId = supplement(playerToken, id, 201, CONTENT).get("id").asText();
        mockMvc.perform(put("/api/v1/booking-disputes/" + id + "/supplements/" + supplementId).header("Authorization", bearer(playerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"sua noi dung cu\"}")).andExpect(status().is4xxClientError());
        mockMvc.perform(delete("/api/v1/booking-disputes/" + id + "/supplements/" + supplementId).header("Authorization", bearer(playerToken)))
                .andExpect(status().is4xxClientError());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT reporter_updates_json -> 0 ->> 'content' FROM booking_disputes WHERE id = ?", String.class, id)).isEqualTo(CONTENT);
    }

    @Test
    @DisplayName("Anh cua lan bo sung: tai kem supplementId (ca khi chu san da giai trinh), hien duoi dung lan bo sung, khong xoa duoc, tinh vao tong 10 anh")
    void attachesImagesToASupplement() throws Exception {
        UUID id = dispute();
        String supplementId = supplement(playerToken, id, 201, CONTENT).get("id").asText();
        jdbc.update("UPDATE booking_disputes SET status = 'reviewing', owner_response = 'Da giai trinh' WHERE id = ?", id);
        entityManager.clear();

        JsonNode uploaded = image(playerToken, id, supplementId, 201);
        assertThat(uploaded.get("supplementId").asText()).isEqualTo(supplementId);
        // Anh cua bao cao ban dau thi het duoc tai khi chu san da giai trinh.
        assertThat(image(playerToken, id, null, 409).get("code").asText()).isEqualTo("BOOKING_DISPUTE_INVALID");
        // Lan bo sung khong ton tai.
        image(playerToken, id, UUID.randomUUID().toString(), 404);

        JsonNode timeline = detail(id).get("timeline");
        assertThat(timeline.get(0).get("evidences")).isEmpty();
        assertThat(timeline.get(1).get("evidences")).hasSize(1);
        assertThat(timeline.get(1).get("evidences").get(0).get("supplementId").asText()).isEqualTo(supplementId);
        mockMvc.perform(delete("/api/v1/booking-disputes/" + id + "/evidences/" + uploaded.get("id").asText()).header("Authorization", bearer(playerToken)))
                .andExpect(status().isConflict());

        // Tong anh toi da 10 (ca anh bo sung).
        for (int i = 0; i < 9; i++) image(playerToken, id, supplementId, 201);
        assertThat(image(playerToken, id, supplementId, 409).get("code").asText()).isEqualTo("IMAGE_LIMIT_REACHED");
        assertThat(detail(id).get("evidenceRemaining").asInt()).isZero();
    }

    @Test
    @DisplayName("Dong bao cao thi khong tai them anh cho lan bo sung")
    void noImagesOnceClosed() throws Exception {
        UUID id = dispute();
        String supplementId = supplement(playerToken, id, 201, CONTENT).get("id").asText();
        jdbc.update("UPDATE booking_disputes SET status = 'resolved' WHERE id = ?", id);
        entityManager.clear();
        assertThat(image(playerToken, id, supplementId, 409).get("code").asText()).isEqualTo("BOOKING_DISPUTE_INVALID");
    }
}
