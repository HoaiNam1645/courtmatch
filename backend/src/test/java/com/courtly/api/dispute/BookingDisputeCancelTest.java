package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.92 - huy bao cao khi chu san chua giai trinh. */
class BookingDisputeCancelTest extends BookingDisputeTestBase {

    private static final String DESCRIPTION = "San dong cua khi toi den luc 18h, khong ai mo cong";

    private UUID bookingId;

    private UUID dispute() throws Exception {
        bookingId = booking(playerToken, BookingStatus.COMPLETED, 1);
        return UUID.fromString(dispute(playerToken, bookingId, 201, "venue_closed", DESCRIPTION).get("id").asText());
    }

    private JsonNode cancel(String token, UUID id, int expected) throws Exception {
        var request = post("/api/v1/booking-disputes/" + id + "/cancel");
        if (token != null) request = request.header("Authorization", bearer(token));
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Huy khi chu san chua giai trinh: cancelled, giu nguyen noi dung/anh/bo sung, ghi audit, bao chu san, het canCancel va canSupplement")
    void cancelsAnUnansweredReport() throws Exception {
        UUID id = dispute();
        jdbc.update("""
                UPDATE booking_disputes SET refund_requested = true, requested_refund_amount = 50000,
                  reporter_updates_json = '[{"id":"u1","content":"Bo sung","createdAt":"2026-01-01T00:00:00Z"}]'::jsonb,
                  reporter_evidences_json = '[{"id":"22222222-2222-4222-8222-222222222222","fileName":"x.jpg","originalName":"a.jpg","size":3,"contentType":"image/jpeg","uploadedAt":"2026-01-01T00:00:00Z","supplementId":null}]'::jsonb
                WHERE id = ?
                """, id);
        entityManager.clear();

        JsonNode detail = cancel(playerToken, id, 200);
        assertThat(detail.get("status").asText()).isEqualTo("cancelled");
        assertThat(detail.get("closed").asBoolean()).isTrue();
        assertThat(detail.get("canCancel").asBoolean()).isFalse();
        assertThat(detail.get("canSupplement").asBoolean()).isFalse();
        assertThat(detail.get("timeline")).hasSize(2);

        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM booking_disputes WHERE id = ?", String.class, id)).isEqualTo("cancelled");
        assertThat(jdbc.queryForObject("SELECT description FROM booking_disputes WHERE id = ?", String.class, id)).isEqualTo(DESCRIPTION);
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(reporter_evidences_json) FROM booking_disputes WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT jsonb_array_length(reporter_updates_json) FROM booking_disputes WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'dispute.cancel' AND entity_id = ? AND actor_id = ?",
                Integer.class, id, player.getId())).isEqualTo(1);
        JsonNode data = objectMapper.readTree(jdbc.queryForObject(
                "SELECT data_json::text FROM notifications WHERE user_id = ? AND type = 'booking_dispute_cancelled'", String.class, owner.getId()));
        assertThat(data.get("disputeId").asText()).isEqualTo(id.toString());
        // Khong tao hoan tien, khong doi don.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refunds WHERE booking_id = ?", Integer.class, bookingId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId)).isEqualTo("completed");
    }

    @Test
    @DisplayName("Huy xong thi bao cao lai duoc don nay (con han); khong huy lai, khong bo sung, khong tai them anh")
    void canReportAgainAfterCancelling() throws Exception {
        UUID id = dispute();
        cancel(playerToken, id, 200);
        assertThat(cancel(playerToken, id, 409).get("code").asText()).isEqualTo("BOOKING_DISPUTE_INVALID");
        dispute(playerToken, bookingId, 201, "extra_charge", "Bi thu them tien nuoc ngoai gia niem yet");
    }

    @Test
    @DisplayName("Khong huy duoc khi chu san da giai trinh, dang duoc quan tri vien xem xet hoac da dong (409), du lieu giu nguyen")
    void cannotCancelOnceTheOwnerAnswered() throws Exception {
        UUID answered = dispute();
        jdbc.update("UPDATE booking_disputes SET owner_response = 'Da giai trinh', owner_responded_at = now() WHERE id = ?", answered);
        entityManager.clear();
        assertThat(cancel(playerToken, answered, 409).get("code").asText()).isEqualTo("BOOKING_DISPUTE_INVALID");

        UUID other = dispute();
        for (String state : new String[] {"reviewing", "resolved", "rejected", "cancelled"}) {
            jdbc.update("UPDATE booking_disputes SET status = ? WHERE id = ?", state, other);
            entityManager.clear();
            cancel(playerToken, other, 409);
        }
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'dispute.cancel'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'booking_dispute_cancelled'",
                Integer.class, owner.getId())).isZero();
    }

    @Test
    @DisplayName("Chi nguoi bao cao huy duoc: nguoi khac, chu san, id la 404; chua dang nhap 401")
    void onlyTheReporter() throws Exception {
        UUID id = dispute();
        cancel(loginAs(persistUser("cancel-stranger", Role.PLAYER)), id, 404);
        cancel(ownerToken, id, 404);
        cancel(playerToken, UUID.randomUUID(), 404);
        cancel(null, id, 401);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM booking_disputes WHERE id = ?", String.class, id)).isEqualTo("awaiting_owner_response");
    }
}
