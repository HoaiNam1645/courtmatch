package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.93 - chu san xem danh sach bao cao; 2.3.94 - chi tiet kem bang chung cua nguoi choi. */
class OwnerDisputeViewTest extends BookingDisputeTestBase {

    private static final String PATH = "/api/v1/owner/booking-disputes";
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

    private void set(String id, String sql, Object... args) {
        Object[] all = new Object[args.length + 1];
        System.arraycopy(args, 0, all, 0, args.length);
        all[args.length] = UUID.fromString(id);
        jdbc.update("UPDATE booking_disputes SET " + sql + " WHERE id = ?", all);
        entityManager.clear();
    }

    @Test
    @DisplayName("Danh sach: chi bao cao ve don thuoc san cua minh, cho giai trinh len dau theo han gan nhat, kem nguoi choi, hoan tien, anh")
    void listsTheOwnVenueReports() throws Exception {
        String late = report(playerToken, 3, "venue_closed");
        String soon = report(playerToken, 2, "extra_charge");
        String answered = report(playerToken, 1, "venue_mismatch");
        set(late, "ownerResponseDeadline = now() + interval '40 hours'".replace("ownerResponseDeadline", "owner_response_deadline"));
        set(soon, "owner_response_deadline = now() + interval '3 hours', refund_requested = true, requested_refund_amount = 60000");
        set(answered, "status = 'reviewing', owner_response = 'Da giai trinh', owner_responded_at = now()");
        set(late, "reporter_evidences_json = '[{\"id\":\"a\"},{\"id\":\"b\"}]'::jsonb");

        // Bao cao cua chu san khac khong lan vao.
        User otherOwner = persistOwner("other-dispute-owner");
        Venue otherVenue = persistVenue(otherOwner, "San Cua Chu Khac");
        Court saved = court;
        court = otherVenue.getCourts().getFirst();
        String foreign = report(playerToken, 4, "other");
        court = saved;
        assertThat(foreign).isNotBlank();

        JsonNode page = call(ownerToken, "", 200);
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        JsonNode items = page.get("items");
        assertThat(items.get(0).get("id").asText()).isEqualTo(soon);
        assertThat(items.get(1).get("id").asText()).isEqualTo(late);
        assertThat(items.get(2).get("id").asText()).isEqualTo(answered);
        JsonNode first = items.get(0);
        assertThat(first.get("playerName").asText()).startsWith("Nguoi Dung reporter");
        assertThat(first.get("venueName").asText()).isEqualTo(venue.getName());
        assertThat(first.get("categoryLabel").asText()).isEqualTo("Bị thu thêm tiền");
        assertThat(first.get("status").asText()).isEqualTo("awaiting_owner_response");
        assertThat(first.get("refundRequested").asBoolean()).isTrue();
        assertThat(first.get("refundAmount").asInt()).isEqualTo(60000);
        assertThat(first.get("expiringSoon").asBoolean()).isTrue();
        assertThat(first.get("overdue").asBoolean()).isFalse();
        assertThat(first.get("ownerResponseDeadline").asText()).isNotBlank();
        assertThat(items.get(1).get("evidenceCount").asInt()).isEqualTo(2);
        assertThat(items.get(1).get("expiringSoon").asBoolean()).isFalse();
        assertThat(items.get(2).has("ownerResponseDeadline")).isFalse();
        assertThat(page.toString()).doesNotContain("@example.com");

        JsonNode foreignView = call(loginAs(otherOwner), "", 200);
        assertThat(foreignView.get("totalElements").asLong()).isEqualTo(1);
        assertThat(foreignView.get("items").get(0).get("id").asText()).isEqualTo(foreign);
    }

    @Test
    @DisplayName("Bo dem: dang cho giai trinh, sap het han (duoi 6 gio), da qua han; qua han duoc danh dau overdue")
    void countsAndOverdue() throws Exception {
        String waiting = report(playerToken, 1, "venue_closed");
        String soon = report(playerToken, 2, "venue_closed");
        String overdue = report(playerToken, 3, "venue_closed");
        set(waiting, "owner_response_deadline = now() + interval '30 hours'");
        set(soon, "owner_response_deadline = now() + interval '5 hours'");
        set(overdue, "owner_response_deadline = now() - interval '1 hour'");

        JsonNode page = call(ownerToken, "", 200);
        JsonNode counts = page.get("counts");
        assertThat(counts.get("awaiting").asLong()).isEqualTo(3);
        assertThat(counts.get("expiringSoon").asLong()).isEqualTo(1);
        assertThat(counts.get("overdue").asLong()).isEqualTo(1);
        JsonNode overdueItem = null;
        for (JsonNode item : page.get("items")) if (item.get("id").asText().equals(overdue)) overdueItem = item;
        assertThat(overdueItem.get("overdue").asBoolean()).isTrue();
        assertThat(overdueItem.get("expiringSoon").asBoolean()).isFalse();
        // Bo dem khong phu thuoc bo loc.
        assertThat(call(ownerToken, "?status=closed", 200).get("counts").get("awaiting").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("Loc theo trang thai va san, phan trang; san cua chu san khac 404; tham so sai 400; nguoi choi 403; chua dang nhap 401")
    void filtersPaginatesAndValidates() throws Exception {
        String waiting = report(playerToken, 1, "venue_closed");
        String reviewing = report(playerToken, 2, "venue_closed");
        String resolved = report(playerToken, 3, "venue_closed");
        set(reviewing, "status = 'reviewing'");
        set(resolved, "status = 'resolved', resolution = 'refund_full'");

        assertThat(call(ownerToken, "?status=awaiting", 200).get("totalElements").asLong()).isEqualTo(1);
        assertThat(call(ownerToken, "?status=answered", 200).get("items").get(0).get("id").asText()).isEqualTo(reviewing);
        JsonNode closed = call(ownerToken, "?status=closed", 200);
        assertThat(closed.get("items").get(0).get("id").asText()).isEqualTo(resolved);
        assertThat(closed.get("items").get(0).get("resolution").asText()).isEqualTo("refund_full");
        assertThat(call(ownerToken, "?venueId=" + venue.getId(), 200).get("totalElements").asLong()).isEqualTo(3);
        assertThat(call(ownerToken, "?size=2&page=1", 200).get("items")).hasSize(1);
        assertThat(waiting).isNotBlank();

        call(ownerToken, "?venueId=" + UUID.randomUUID(), 404);
        call(loginAs(persistOwner("dispute-stranger-owner")), "?venueId=" + venue.getId(), 404);
        call(ownerToken, "?status=weird", 400);
        call(ownerToken, "?size=51", 400);
        call(ownerToken, "?page=-1", 400);
        call(playerToken, "", 403);
        call(null, "", 401);
    }

    @Test
    @DisplayName("Chi tiet: don, ho ten va so dien thoai nguoi choi (khong email), noi dung, anh va bo sung; dong thoi gian nhin tu chu san")
    void showsTheDetail() throws Exception {
        String id = report(playerToken, 1, "extra_charge");
        jdbc.update("UPDATE users SET phone = '0912345678' WHERE id = ?", player.getId());
        set(id, """
                refund_requested = true, requested_refund_amount = 90000,
                reporter_evidences_json = jsonb_build_array(jsonb_build_object('id', '22222222-2222-4222-8222-222222222222', 'fileName', 'x.jpg',
                    'originalName', 'cong.jpg', 'size', 10, 'contentType', 'image/jpeg', 'uploadedAt', '2026-01-01T00:00:00Z', 'supplementId', null)),
                reporter_updates_json = jsonb_build_array(jsonb_build_object('id', 's1', 'content', 'Nguoi choi bo sung', 'createdAt', '2999-01-01T00:00:00Z')),
                owner_response = 'Chu san giai trinh', owner_responded_at = '2999-01-02T00:00:00Z', status = 'resolved', resolution = 'refund_half',
                resolution_note = 'Hoan mot nua', handled_at = '2999-01-03T00:00:00Z'
                """);

        JsonNode detail = call(ownerToken, "/" + id, 200);
        assertThat(detail.get("player").get("fullName").asText()).startsWith("Nguoi Dung reporter");
        assertThat(detail.get("player").get("phone").asText()).isEqualTo("0912345678");
        assertThat(detail.get("player").has("email")).isFalse();
        assertThat(detail.toString()).doesNotContain(player.getEmail());
        assertThat(detail.get("booking").get("venueName").asText()).isEqualTo(venue.getName());
        assertThat(detail.get("booking").get("payableAmount").asInt()).isPositive();
        assertThat(detail.get("refundAmount").asInt()).isEqualTo(90000);
        JsonNode timeline = detail.get("timeline");
        assertThat(timeline.findValuesAsText("type")).containsExactly("created", "supplement", "owner_response", "resolution");
        assertThat(timeline.findValuesAsText("actor")).containsExactly("player", "player", "you", "admin");
        assertThat(timeline.get(0).get("evidences").get(0).get("url").asText()).endsWith("/22222222-2222-4222-8222-222222222222/file");
        assertThat(detail.get("canRespond").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("canRespond: chi khi cho giai trinh, chua qua han va chua giai trinh")
    void canRespondOnlyBeforeTheDeadline() throws Exception {
        String id = report(playerToken, 1, "venue_closed");
        assertThat(call(ownerToken, "/" + id, 200).get("canRespond").asBoolean()).isTrue();
        set(id, "owner_response_deadline = now() - interval '1 minute'");
        JsonNode late = call(ownerToken, "/" + id, 200);
        assertThat(late.get("canRespond").asBoolean()).isFalse();
        assertThat(late.get("overdue").asBoolean()).isTrue();
        set(id, "owner_response_deadline = now() + interval '1 hour', owner_response = 'Da giai trinh', status = 'reviewing'");
        assertThat(call(ownerToken, "/" + id, 200).get("canRespond").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Bao cao ve san cua chu san khac, id la 404; nguoi choi 403; chua dang nhap 401")
    void detailAccess() throws Exception {
        String id = report(playerToken, 1, "venue_closed");
        call(loginAs(persistOwner("detail-stranger-owner")), "/" + id, 404);
        call(ownerToken, "/" + UUID.randomUUID(), 404);
        call(playerToken, "/" + id, 403);
        call(null, "/" + id, 401);
        assertThat(loginAs(persistUser("not-an-owner", Role.PLAYER))).isNotBlank();
    }
}
