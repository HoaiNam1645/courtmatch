package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** 2.3.95 - chu san giai trinh; 2.3.96 - anh bang chung cua chu san. */
class OwnerDisputeRespondTest extends BookingDisputeTestBase {

    private static final String PATH = "/api/v1/owner/booking-disputes/";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final String TEXT = "Chung toi co mo cua dung gio, khach den sai khung gio da dat";

    private String report() throws Exception {
        UUID bookingId = booking(playerToken, BookingStatus.COMPLETED, 1);
        return dispute(playerToken, bookingId, 201, "venue_closed", "San dong cua khi toi den luc 18h").get("id").asText();
    }

    private JsonNode respond(String token, String id, String content, int expected) throws Exception {
        String body = content == null ? "{}" : "{\"content\":\"" + content + "\"}";
        return objectMapper.readTree(mockMvc.perform(post(PATH + id + "/response").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode upload(String token, String id, byte[] bytes, int expected) throws Exception {
        var file = new MockMultipartFile("file", "a.png", "application/octet-stream", bytes);
        return objectMapper.readTree(mockMvc.perform(multipart(PATH + id + "/evidences").file(file).header("Authorization", bearer(token)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode detail(String token, String id) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(PATH + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    @DisplayName("Giai trinh: luu noi dung, chuyen sang quan tri vien xem xet, bao nguoi choi, ghi audit; chi mot lan")
    void respondsOnce() throws Exception {
        String id = report();
        JsonNode result = respond(ownerToken, id, TEXT, 200);
        assertThat(result.get("status").asText()).isEqualTo("reviewing");
        assertThat(result.get("canRespond").asBoolean()).isFalse();
        assertThat(result.get("timeline").get(1).get("type").asText()).isEqualTo("owner_response");
        assertThat(result.get("timeline").get(1).get("actor").asText()).isEqualTo("you");
        entityManager.flush();
        assertThat(count("SELECT count(*) FROM notifications WHERE type = 'booking_dispute_responded' AND user_id = ?", player.getId())).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'dispute.owner_response' AND entity_id = ?", UUID.fromString(id))).isEqualTo(1);
        respond(ownerToken, id, TEXT, 409);
        assertThat(jdbc.queryForObject("SELECT owner_response FROM booking_disputes WHERE id = ?", String.class, UUID.fromString(id))).isEqualTo(TEXT);
    }

    @Test
    @DisplayName("Giai trinh: 10-2000 ky tu, khong gui khi qua han, nguoi khac va chu san khac nhan 404")
    void validatesAndGuards() throws Exception {
        String id = report();
        respond(ownerToken, id, null, 400);
        respond(ownerToken, id, "ngan qua", 400);
        respond(ownerToken, id, "x".repeat(2001), 400);
        respond(playerToken, id, TEXT, 403);
        var other = persistOwner("other-owner");
        respond(loginAs(other), id, TEXT, 404);

        jdbc.update("UPDATE booking_disputes SET owner_response_deadline = now() - interval '1 minute' WHERE id = ?", UUID.fromString(id));
        entityManager.clear();
        respond(ownerToken, id, TEXT, 409);
        upload(ownerToken, id, PNG, 409);
        assertThat(detail(ownerToken, id).get("canRespond").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Anh giai trinh: toi da 5 anh, xoa duoc truoc khi gui, hien trong giai trinh, khoa sau khi gui, khong dung dinh voi anh nguoi choi")
    void ownerEvidence() throws Exception {
        String id = report();
        upload(playerToken, id, PNG, 403);
        upload(ownerToken, id, new byte[] {1, 2, 3}, 400);
        String first = upload(ownerToken, id, PNG, 201).get("id").asText();
        for (int i = 0; i < 4; i++) upload(ownerToken, id, PNG, 201);
        upload(ownerToken, id, PNG, 409);
        assertThat(detail(ownerToken, id).get("draftEvidences")).hasSize(5);

        mockMvc.perform(delete(PATH + id + "/evidences/" + first).header("Authorization", bearer(ownerToken))).andExpect(status().isNoContent());
        assertThat(detail(ownerToken, id).get("draftEvidences")).hasSize(4);
        var otherOwner = loginAs(persistOwner("other-owner-2"));
        mockMvc.perform(delete(PATH + id + "/evidences/" + first).header("Authorization", bearer(otherOwner))).andExpect(status().isNotFound());

        JsonNode result = respond(ownerToken, id, TEXT, 200);
        assertThat(result.get("timeline").get(1).get("evidences")).hasSize(4);
        assertThat(result.get("draftEvidences")).isEmpty();
        upload(ownerToken, id, PNG, 409);
        String remaining = result.get("timeline").get(1).get("evidences").get(0).get("id").asText();
        mockMvc.perform(delete(PATH + id + "/evidences/" + remaining).header("Authorization", bearer(ownerToken))).andExpect(status().isConflict());

        // Nguoi choi xem duoc anh chu san sau khi gui, nguoi la thi khong.
        String url = result.get("timeline").get(1).get("evidences").get(0).get("url").asText();
        mockMvc.perform(get(url).header("Authorization", bearer(playerToken))).andExpect(status().isOk());
        var stranger = loginAs(persistUser("stranger", Role.PLAYER));
        mockMvc.perform(get(url).header("Authorization", bearer(stranger))).andExpect(status().isNotFound());
    }
}
