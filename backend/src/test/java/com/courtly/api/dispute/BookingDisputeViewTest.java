package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.89 - danh sach bao cao da gui; 2.3.90 - chi tiet kem noi dung trao doi. */
class BookingDisputeViewTest extends BookingDisputeTestBase {

    private static final String PATH = "/api/v1/booking-disputes";
    private static final String DESCRIPTION = "San dong cua khi toi den luc 18h, khong ai mo cong";

    private JsonNode call(String token, String path, int expected) throws Exception {
        var request = get(PATH + path);
        if (token != null) request = request.header("Authorization", bearer(token));
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private String report(String token, int endedDaysAgo, String category) throws Exception {
        UUID bookingId = booking(token, BookingStatus.COMPLETED, endedDaysAgo);
        return dispute(token, bookingId, 201, category, DESCRIPTION).get("id").asText();
    }

    private void setStatus(String disputeId, String status) {
        jdbc.update("UPDATE booking_disputes SET status = ? WHERE id = ?::uuid", status, disputeId);
        entityManager.clear();
    }

    @Test
    @DisplayName("Danh sach: chi bao cao cua chinh minh, moi nhat truoc, kem don, san, loai, hoan tien, so anh, han giai trinh")
    void listsOwnReports() throws Exception {
        String first = report(playerToken, 3, "venue_closed");
        String second = report(playerToken, 2, "extra_charge");
        String stranger = loginAs(persistUser("view-stranger", Role.PLAYER));
        report(stranger, 1, "venue_mismatch");
        jdbc.update("UPDATE booking_disputes SET created_at = created_at - interval '1 hour' WHERE id = ?::uuid", first);
        jdbc.update("UPDATE booking_disputes SET refund_requested = true, requested_refund_amount = 60000 WHERE id = ?::uuid", second);
        jdbc.update("UPDATE booking_disputes SET reporter_evidences_json = '[{\"id\":\"a\"},{\"id\":\"b\"}]'::jsonb WHERE id = ?::uuid", second);
        entityManager.clear();

        JsonNode page = call(playerToken, "", 200);
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        JsonNode newest = page.get("content").get(0);
        assertThat(newest.get("id").asText()).isEqualTo(second);
        assertThat(page.get("content").get(1).get("id").asText()).isEqualTo(first);
        assertThat(newest.get("bookingCode").asText()).isNotBlank();
        assertThat(newest.get("venueName").asText()).isEqualTo(venue.getName());
        assertThat(newest.get("category").asText()).isEqualTo("extra_charge");
        assertThat(newest.get("categoryLabel").asText()).isEqualTo("Bị thu thêm tiền");
        assertThat(newest.get("status").asText()).isEqualTo("awaiting_owner_response");
        assertThat(newest.get("closed").asBoolean()).isFalse();
        assertThat(newest.get("refundRequested").asBoolean()).isTrue();
        assertThat(newest.get("refundAmount").asInt()).isEqualTo(60000);
        assertThat(newest.get("evidenceCount").asInt()).isEqualTo(2);
        assertThat(newest.get("ownerResponseDeadline").asText()).isNotBlank();
        assertThat(page.get("content").get(1).get("evidenceCount").asInt()).isZero();
        assertThat(page.toString()).doesNotContain(stranger);
    }

    @Test
    @DisplayName("Loc dang xu ly / da dong; han giai trinh chi hien khi dang cho chu san; ket luan hien khi da dong")
    void filtersActiveAndClosed() throws Exception {
        String waiting = report(playerToken, 1, "venue_closed");
        String reviewing = report(playerToken, 2, "venue_mismatch");
        String resolved = report(playerToken, 3, "extra_charge");
        String rejected = report(playerToken, 4, "other");
        String cancelled = report(playerToken, 5, "owner_cancelled");
        setStatus(reviewing, "reviewing");
        setStatus(resolved, "resolved");
        jdbc.update("UPDATE booking_disputes SET resolution = 'refund_full' WHERE id = ?::uuid", resolved);
        setStatus(rejected, "rejected");
        setStatus(cancelled, "cancelled");

        assertThat(call(playerToken, "?status=active", 200).get("totalElements").asLong()).isEqualTo(2);
        JsonNode closed = call(playerToken, "?status=closed", 200);
        assertThat(closed.get("totalElements").asLong()).isEqualTo(3);
        closed.get("content").forEach(item -> {
            assertThat(item.get("closed").asBoolean()).isTrue();
            assertThat(item.has("ownerResponseDeadline")).isFalse();
        });
        JsonNode resolvedItem = null;
        for (JsonNode item : closed.get("content")) if (item.get("id").asText().equals(resolved)) resolvedItem = item;
        assertThat(resolvedItem.get("resolution").asText()).isEqualTo("refund_full");
        assertThat(call(playerToken, "?status=all", 200).get("totalElements").asLong()).isEqualTo(5);
        assertThat(call(playerToken, "", 200).get("totalElements").asLong()).isEqualTo(5);
        for (JsonNode item : call(playerToken, "?status=active", 200).get("content")) {
            assertThat(item.get("id").asText()).isIn(waiting, reviewing);
            if (item.get("id").asText().equals(reviewing)) assertThat(item.has("ownerResponseDeadline")).isFalse();
        }
    }

    @Test
    @DisplayName("Phan trang, tham so sai bi tu choi, chua dang nhap 401; nguoi chua co bao cao nhan danh sach rong")
    void paginatesAndValidates() throws Exception {
        for (int i = 1; i <= 3; i++) report(playerToken, i, "venue_closed");
        JsonNode page = call(playerToken, "?size=2&page=1", 200);
        assertThat(page.get("content")).hasSize(1);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        call(playerToken, "?status=weird", 400);
        call(playerToken, "?size=51", 400);
        call(playerToken, "?size=0", 400);
        call(playerToken, "?page=-1", 400);
        call(null, "", 401);
        JsonNode none = call(loginAs(persistUser("view-empty", Role.PLAYER)), "", 200);
        assertThat(none.get("totalElements").asLong()).isZero();
        assertThat(none.get("content")).isEmpty();
    }

    @Test
    @DisplayName("Chi tiet: don, noi dung ban dau, hoan tien, han giai trinh, anh; cac viec con lam duoc khi cho chu san")
    void showsDetail() throws Exception {
        String id = report(playerToken, 1, "venue_closed");
        jdbc.update("UPDATE booking_disputes SET refund_requested = true, requested_refund_amount = 90000 WHERE id = ?::uuid", id);
        jdbc.update("""
                UPDATE booking_disputes SET reporter_evidences_json = jsonb_build_array(jsonb_build_object('id', '22222222-2222-4222-8222-222222222222',
                  'fileName', 'x.jpg', 'originalName', 'cong.jpg', 'size', 10, 'contentType', 'image/jpeg', 'uploadedAt', to_char((now()) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'), 'supplementId', null))
                WHERE id = ?::uuid
                """, id);
        entityManager.clear();

        JsonNode detail = call(playerToken, "/" + id, 200);
        assertThat(detail.get("categoryLabel").asText()).isEqualTo("Sân đóng cửa");
        assertThat(detail.get("status").asText()).isEqualTo("awaiting_owner_response");
        assertThat(detail.get("refundAmount").asInt()).isEqualTo(90000);
        assertThat(detail.get("booking").get("venueName").asText()).isEqualTo(venue.getName());
        assertThat(detail.get("booking").get("bookingCode").asText()).isNotBlank();
        assertThat(detail.get("booking").get("courtName").asText()).isNotBlank();
        assertThat(detail.get("booking").get("payableAmount").asInt()).isPositive();
        assertThat(detail.get("canSupplement").asBoolean()).isTrue();
        assertThat(detail.get("canCancel").asBoolean()).isTrue();
        assertThat(detail.get("supplementsRemaining").asInt()).isEqualTo(10);
        assertThat(detail.get("evidenceRemaining").asInt()).isEqualTo(9);
        JsonNode timeline = detail.get("timeline");
        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).get("type").asText()).isEqualTo("created");
        assertThat(timeline.get(0).get("actor").asText()).isEqualTo("you");
        assertThat(timeline.get(0).get("text").asText()).isEqualTo(DESCRIPTION);
        JsonNode evidence = timeline.get(0).get("evidences").get(0);
        assertThat(evidence.get("originalName").asText()).isEqualTo("cong.jpg");
        assertThat(evidence.get("url").asText()).isEqualTo("/api/v1/booking-disputes/" + id + "/evidences/22222222-2222-4222-8222-222222222222/file");
        assertThat(evidence.has("fileName")).isFalse();
        assertThat(detail.toString()).doesNotContain("x.jpg");
    }

    @Test
    @DisplayName("Dong thoi gian ghep du: tao bao cao, bo sung, giai trinh cua chu san, quyet dinh cua quan tri vien, theo thu tu thoi gian")
    void buildsTheWholeConversation() throws Exception {
        String id = report(playerToken, 1, "extra_charge");
        jdbc.update("""
                UPDATE booking_disputes SET
                  reporter_updates_json = jsonb_build_array(jsonb_build_object('id', 's1', 'content', 'Bo sung: co tin nhan chung minh', 'createdAt', to_char((now() + interval '1 hour') AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'))),
                  owner_response = 'Chu san giai trinh: toi chi thu tien thue vot', owner_responded_at = now() + interval '2 hours',
                  owner_evidences_json = jsonb_build_array(jsonb_build_object('id', '33333333-3333-4333-8333-333333333333', 'fileName', 'o.jpg',
                       'originalName', 'bien-lai.jpg', 'size', 5, 'contentType', 'image/jpeg', 'uploadedAt', to_char((now()) AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"'))),
                  status = 'resolved', resolution = 'refund_half', resolution_note = 'Hoan 50% theo thoa thuan', handled_at = now() + interval '3 hours', handled_by = (SELECT id FROM users LIMIT 1)
                WHERE id = ?::uuid
                """, id);
        entityManager.clear();

        JsonNode detail = call(playerToken, "/" + id, 200);
        JsonNode timeline = detail.get("timeline");
        assertThat(timeline).hasSize(4);
        assertThat(timeline.findValuesAsText("type")).containsExactly("created", "supplement", "owner_response", "resolution");
        assertThat(timeline.findValuesAsText("actor")).containsExactly("you", "you", "owner", "admin");
        assertThat(timeline.get(1).get("text").asText()).isEqualTo("Bo sung: co tin nhan chung minh");
        assertThat(timeline.get(2).get("text").asText()).contains("thue vot");
        assertThat(timeline.get(2).get("evidences").get(0).get("originalName").asText()).isEqualTo("bien-lai.jpg");
        assertThat(timeline.get(3).get("label").asText()).isEqualTo("refund_half");
        assertThat(timeline.get(3).get("text").asText()).isEqualTo("Hoan 50% theo thoa thuan");
        assertThat(detail.get("closed").asBoolean()).isTrue();
        assertThat(detail.get("canSupplement").asBoolean()).isFalse();
        assertThat(detail.get("canCancel").asBoolean()).isFalse();
        assertThat(detail.get("supplementsRemaining").asInt()).isEqualTo(9);
    }

    @Test
    @DisplayName("Sau khi chu san giai trinh: con bo sung duoc nhung khong huy duoc; het 10 lan thi khong bo sung nua")
    void reflectsWhatThePlayerCanStillDo() throws Exception {
        String id = report(playerToken, 1, "venue_closed");
        jdbc.update("UPDATE booking_disputes SET status = 'reviewing', owner_response = 'Da giai trinh', owner_responded_at = now() WHERE id = ?::uuid", id);
        entityManager.clear();
        JsonNode reviewing = call(playerToken, "/" + id, 200);
        assertThat(reviewing.get("canSupplement").asBoolean()).isTrue();
        assertThat(reviewing.get("canCancel").asBoolean()).isFalse();

        StringBuilder updates = new StringBuilder("[");
        for (int i = 0; i < 10; i++) updates.append(i == 0 ? "" : ",").append("{\"id\":\"u").append(i).append("\",\"content\":\"n\",\"createdAt\":\"2026-01-01T00:00:00Z\"}");
        jdbc.update("UPDATE booking_disputes SET reporter_updates_json = ?::jsonb WHERE id = ?::uuid", updates.append("]").toString(), id);
        entityManager.clear();
        JsonNode full = call(playerToken, "/" + id, 200);
        assertThat(full.get("canSupplement").asBoolean()).isFalse();
        assertThat(full.get("supplementsRemaining").asInt()).isZero();
    }

    @Test
    @DisplayName("Bao cao cua nguoi khac, chu san, id la deu 404; chua dang nhap 401")
    void onlyTheReporterSeesTheDetail() throws Exception {
        String id = report(playerToken, 1, "venue_closed");
        call(loginAs(persistUser("detail-stranger", Role.PLAYER)), "/" + id, 404);
        call(ownerToken, "/" + id, 404);
        call(playerToken, "/" + UUID.randomUUID(), 404);
        call(null, "/" + id, 401);
    }
}
