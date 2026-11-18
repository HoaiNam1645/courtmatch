package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/** 2.3.88 - anh bang chung cua bao cao don dat san. */
class BookingDisputeEvidenceTest extends BookingDisputeTestBase {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    @Value("${courtly.storage.upload-dir}") private String uploadDir;

    private UUID dispute() throws Exception {
        UUID bookingId = booking(playerToken, BookingStatus.COMPLETED, 1);
        return UUID.fromString(dispute(playerToken, bookingId, 201, "venue_closed", "San dong cua khi toi den luc 18h, khong ai mo").get("id").asText());
    }

    private MvcResult upload(String token, UUID disputeId, String name, byte[] bytes, int expected) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", name, "application/octet-stream", bytes);
        var builder = multipart("/api/v1/booking-disputes/" + disputeId + "/evidences").file(file);
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request = builder;
        if (token != null) request = builder.header("Authorization", bearer(token));
        return mockMvc.perform(request).andExpect(status().is(expected)).andReturn();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Path fileOf(UUID disputeId, String fileName) {
        return Path.of(uploadDir).toAbsolutePath().resolve("disputes").resolve(disputeId.toString()).resolve(fileName);
    }

    private String storedFileName(UUID disputeId, int index) {
        return jdbc.queryForObject("SELECT reporter_evidences_json -> ? ->> 'fileName' FROM booking_disputes WHERE id = ?", String.class, index, disputeId);
    }

    @Test
    @DisplayName("Tai len anh: 201, luu ten server sinh (khong dung ten file gui len), ghi vao reporter_evidences_json, file nam tren dia")
    void uploadsAnImage() throws Exception {
        UUID id = dispute();
        JsonNode created = json(upload(playerToken, id, "../../anh san dong cua.jpg", JPEG, 201));
        assertThat(created.get("originalName").asText()).isEqualTo("anh san dong cua.jpg");
        assertThat(created.get("size").asInt()).isEqualTo(JPEG.length);
        assertThat(created.get("contentType").asText()).isEqualTo("image/jpeg");
        assertThat(created.get("url").asText()).isEqualTo("/api/v1/booking-disputes/" + id + "/evidences/" + created.get("id").asText() + "/file");
        assertThat(created.has("supplementId")).isFalse();
        assertThat(created.has("fileName")).isFalse();

        entityManager.flush();
        String stored = storedFileName(id, 0);
        assertThat(stored).matches("[0-9a-f-]{36}\\.jpg").doesNotContain("anh");
        assertThat(Files.readAllBytes(fileOf(id, stored))).isEqualTo(JPEG);
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(reporter_evidences_json) FROM booking_disputes WHERE id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("Chap nhan JPEG, PNG, WebP theo noi dung that; GIF, PDF, text doi duoi .jpg, file rong va file qua 5MB bi 400 IMAGE_INVALID")
    void validatesTheFile() throws Exception {
        UUID id = dispute();
        upload(playerToken, id, "a.png", PNG, 201);
        upload(playerToken, id, "b.webp", WEBP, 201);
        byte[] gif = "GIF89a".getBytes();
        byte[] pdf = "%PDF-1.4".getBytes();
        byte[] text = "chi la van ban".getBytes();
        for (byte[] bad : new byte[][] {gif, pdf, text, new byte[0]}) {
            JsonNode error = json(upload(playerToken, id, "gia-mao.jpg", bad, 400));
            assertThat(error.get("code").asText()).isEqualTo("IMAGE_INVALID");
            assertThat(error.get("fieldErrors").has("file")).isTrue();
        }
        byte[] tooBig = Arrays.copyOf(JPEG, 5 * 1024 * 1024 + 1);
        assertThat(json(upload(playerToken, id, "lon.jpg", tooBig, 400)).get("fieldErrors").get("file").asText()).contains("5MB");
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(reporter_evidences_json) FROM booking_disputes WHERE id = ?", Integer.class, id)).isEqualTo(2);
    }

    @Test
    @DisplayName("Bao cao ban dau toi da 5 anh (IMAGE_LIMIT_REACHED); xoa bot thi tai them duoc")
    void limitsTheInitialImages() throws Exception {
        UUID id = dispute();
        String first = null;
        for (int i = 0; i < 5; i++) {
            String evidenceId = json(upload(playerToken, id, "a" + i + ".jpg", JPEG, 201)).get("id").asText();
            if (first == null) first = evidenceId;
        }
        assertThat(json(upload(playerToken, id, "thu-sau.jpg", JPEG, 409)).get("code").asText()).isEqualTo("IMAGE_LIMIT_REACHED");
        mockMvc.perform(delete("/api/v1/booking-disputes/" + id + "/evidences/" + first).header("Authorization", bearer(playerToken)))
                .andExpect(status().isNoContent());
        upload(playerToken, id, "thay-the.jpg", JPEG, 201);
    }

    @Test
    @DisplayName("Xoa anh: bien mat khoi danh sach va khoi dia; id la 404; anh cua phan bo sung va bao cao da qua buoc cho giai trinh khong xoa/tai duoc")
    void deletesImages() throws Exception {
        UUID id = dispute();
        JsonNode created = json(upload(playerToken, id, "a.jpg", JPEG, 201));
        entityManager.flush();
        String stored = storedFileName(id, 0);
        assertThat(Files.exists(fileOf(id, stored))).isTrue();

        mockMvc.perform(delete("/api/v1/booking-disputes/" + id + "/evidences/" + UUID.randomUUID()).header("Authorization", bearer(playerToken)))
                .andExpect(status().isNotFound());
        // Anh thuoc mot lan bo sung (2.3.91) khong xoa duoc.
        String supplementId = UUID.randomUUID().toString();
        jdbc.update("""
                UPDATE booking_disputes SET reporter_evidences_json = reporter_evidences_json || jsonb_build_array(
                    jsonb_build_object('id', ?::text, 'fileName', ?::text, 'originalName', 'bo-sung.jpg', 'size', 12,
                                       'contentType', 'image/jpeg', 'uploadedAt', now()::text, 'supplementId', ?::text))
                WHERE id = ?
                """, UUID.fromString("11111111-1111-4111-8111-111111111111"), stored, supplementId, id);
        entityManager.clear();
        mockMvc.perform(delete("/api/v1/booking-disputes/" + id + "/evidences/11111111-1111-4111-8111-111111111111").header("Authorization", bearer(playerToken)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/booking-disputes/" + id + "/evidences/" + created.get("id").asText()).header("Authorization", bearer(playerToken)))
                .andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(reporter_evidences_json) FROM booking_disputes WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(Files.exists(fileOf(id, stored))).isFalse();

        // Chu san da giai trinh / bao cao da qua buoc cho: khong tai them, khong xoa.
        for (String state : new String[] {"reviewing", "resolved", "rejected", "cancelled"}) {
            jdbc.update("UPDATE booking_disputes SET status = ? WHERE id = ?", state, id);
            entityManager.clear();
            assertThat(json(upload(playerToken, id, "muon.jpg", JPEG, 409)).get("code").asText()).isEqualTo("BOOKING_DISPUTE_INVALID");
            mockMvc.perform(delete("/api/v1/booking-disputes/" + id + "/evidences/11111111-1111-4111-8111-111111111111")
                    .header("Authorization", bearer(playerToken))).andExpect(status().isConflict());
        }
    }

    @Test
    @DisplayName("Xem anh: nguoi bao cao, chu san cua san va quan tri vien duoc (nosniff, cache rieng tu); nguoi khac 404, chua dang nhap 401")
    void servesImagesOnlyToThePartiesAndAdmins() throws Exception {
        UUID id = dispute();
        String evidenceId = json(upload(playerToken, id, "a.png", PNG, 201)).get("id").asText();
        entityManager.flush();
        entityManager.clear();
        String path = "/api/v1/booking-disputes/" + id + "/evidences/" + evidenceId + "/file";
        ensureRole(Role.ADMIN, "Quan tri vien");
        String adminToken = loginAs(persistUser("evidence-admin", Role.ADMIN));

        for (String token : new String[] {playerToken, ownerToken, adminToken}) {
            MvcResult result = mockMvc.perform(get(path).header("Authorization", bearer(token))).andExpect(status().isOk()).andReturn();
            assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(PNG);
            assertThat(result.getResponse().getContentType()).isEqualTo("image/png");
            assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(result.getResponse().getHeader("Cache-Control")).contains("private").doesNotContain("public");
        }
        String stranger = loginAs(persistUser("evidence-stranger", Role.PLAYER));
        String otherOwner = loginAs(persistOwner("evidence-other-owner"));
        mockMvc.perform(get(path).header("Authorization", bearer(stranger))).andExpect(status().isNotFound());
        mockMvc.perform(get(path).header("Authorization", bearer(otherOwner))).andExpect(status().isNotFound());
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/booking-disputes/" + id + "/evidences/" + UUID.randomUUID() + "/file")
                .header("Authorization", bearer(playerToken))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/booking-disputes/" + UUID.randomUUID() + "/evidences/" + evidenceId + "/file")
                .header("Authorization", bearer(playerToken))).andExpect(status().isNotFound());
        // Khong phuc vu cong khai qua duong dan media nhu anh san.
        mockMvc.perform(get("/api/v1/media/disputes/" + id + "/" + storedFileName(id, 0))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Chi nguoi bao cao tai/xoa duoc: nguoi khac, chu san va id la tra 404; chua dang nhap 401; bao cao tao moi co danh sach anh rong")
    void onlyTheReporterChangesImages() throws Exception {
        UUID id = dispute();
        String evidenceId = json(upload(playerToken, id, "a.jpg", JPEG, 201)).get("id").asText();
        String stranger = loginAs(persistUser("evidence-intruder", Role.PLAYER));
        upload(stranger, id, "x.jpg", JPEG, 404);
        upload(ownerToken, id, "x.jpg", JPEG, 404);
        upload(playerToken, UUID.randomUUID(), "x.jpg", JPEG, 404);
        upload(null, id, "x.jpg", JPEG, 401);
        mockMvc.perform(delete("/api/v1/booking-disputes/" + id + "/evidences/" + evidenceId).header("Authorization", bearer(stranger)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/booking-disputes/" + id + "/evidences/" + evidenceId)).andExpect(status().isUnauthorized());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(reporter_evidences_json) FROM booking_disputes WHERE id = ?", Integer.class, id)).isEqualTo(1);

        JsonNode created = dispute(playerToken, booking(playerToken, BookingStatus.COMPLETED, 3), 201, "venue_closed", "Noi dung bao cao moi khong co anh");
        assertThat(created.get("evidences")).isEmpty();
    }
}
