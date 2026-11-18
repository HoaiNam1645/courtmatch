package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.User;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 2.3.100 - hoan tien toan phan; 2.3.101 - hoan tien mot nua. */
class AdminDisputeDecisionTest extends BookingDisputeTestBase {

    private static final String PATH = "/api/v1/admin/booking-disputes/";
    private static final String NOTE = "Camera cho thay san dong cua dung nhu nguoi choi bao";

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUpAdmin() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("decision-admin");
        adminToken = loginAs(admin);
    }

    private JsonNode decide(String token, String id, String action, String note, int expected) throws Exception {
        String body = note == null ? "{}" : "{\"note\":\"" + note + "\"}";
        return objectMapper.readTree(mockMvc.perform(post(PATH + id + "/" + action).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    /** Bao cao dang cho quan tri vien (reviewing), co khoan thanh toan 180.000d da xac nhan. */
    private String reviewing(int requested) throws Exception {
        UUID bookingId = booking(playerToken, BookingStatus.COMPLETED, 1);
        payment(bookingId, "confirmed");
        String extra = requested > 0 ? "\"refundRequested\":true,\"refundAmount\":" + requested : null;
        String id = dispute(playerToken, bookingId, 201, "extra_charge", "Chu san thu them tien thue vot ngoai thoa thuan", extra).get("id").asText();
        answer(id);
        return id;
    }

    private void answer(String id) {
        jdbc.update("UPDATE booking_disputes SET status = 'reviewing', owner_response = 'Da giai trinh', created_at = now() - interval '2 hours', owner_responded_at = now() - interval '1 hour' WHERE id = ?", UUID.fromString(id));
        entityManager.clear();
    }

    private Map<String, Object> refundOf(String id) {
        entityManager.flush();
        return jdbc.queryForMap("SELECT amount, status, dispute_id, approved_by, requested_by, reason FROM refunds WHERE dispute_id = ?", UUID.fromString(id));
    }

    @Test
    @DisplayName("Hoan toan phan: dung so tien yeu cau, bao cao resolved, tao khoan hoan cho chu san hoan, bao ca hai ben, ghi audit, khong dung hoa hong")
    void refundFull() throws Exception {
        String id = reviewing(125000);
        UUID bookingId = jdbc.queryForObject("SELECT booking_id FROM booking_disputes WHERE id = ?", UUID.class, UUID.fromString(id));
        jdbc.update("INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, due_at) VALUES (?, ?, 180000, 'fixed', 20000, 20000, 'due', now())", bookingId, owner.getId());
        entityManager.clear();

        JsonNode result = decide(adminToken, id, "refund-full", NOTE, 200);
        assertThat(result.get("status").asText()).isEqualTo("resolved");
        assertThat(result.get("resolution").asText()).isEqualTo("refund_full");
        assertThat(result.get("canDecide").asBoolean()).isFalse();
        assertThat(result.get("timeline").get(result.get("timeline").size() - 1).get("actor").asText()).isEqualTo("admin");

        Map<String, Object> refund = refundOf(id);
        assertThat((BigDecimal) refund.get("amount")).isEqualByComparingTo("125000");
        assertThat(refund.get("status")).isEqualTo("awaiting_owner_refund");
        assertThat(refund.get("approved_by")).isEqualTo(admin.getId());
        assertThat(refund.get("requested_by")).isEqualTo(player.getId());
        assertThat(refund.get("reason")).isEqualTo(NOTE);
        assertThat(jdbc.queryForObject("SELECT status || '|' || resolution || '|' || handled_by FROM booking_disputes WHERE id = ?", String.class, UUID.fromString(id)))
                .isEqualTo("resolved|refund_full|" + admin.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE type = 'booking_dispute_resolved' AND user_id IN (?, ?)", Integer.class, owner.getId(), player.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'dispute.refund_full' AND actor_id = ?", Integer.class, admin.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status || '|' || commission_amount FROM booking_commissions WHERE booking_id = ?", String.class, bookingId)).startsWith("due|20000");
        decide(adminToken, id, "refund-full", NOTE, 409);
        decide(adminToken, id, "refund-half", NOTE, 409);
    }

    @Test
    @DisplayName("Hoan mot nua: 50% so yeu cau lam tron xuong 1.000d, toi thieu 1.000d, khong vuot so con hoan duoc")
    void refundHalf() throws Exception {
        String id = reviewing(125000);
        decide(adminToken, id, "refund-half", NOTE, 200);
        assertThat((BigDecimal) refundOf(id).get("amount")).isEqualByComparingTo("62000");
        assertThat(jdbc.queryForObject("SELECT resolution FROM booking_disputes WHERE id = ?", String.class, UUID.fromString(id))).isEqualTo("refund_half");

        String small = reviewing(1000);
        decide(adminToken, small, "refund-half", NOTE, 200);
        assertThat((BigDecimal) refundOf(small).get("amount")).isEqualByComparingTo("1000");

        // Da hoan truoc do 150.000d thi toan phan chi con 30.000d (so con co the hoan).
        String capped = reviewing(100000);
        UUID bookingId = jdbc.queryForObject("SELECT booking_id FROM booking_disputes WHERE id = ?", UUID.class, UUID.fromString(capped));
        jdbc.update("INSERT INTO refunds (payment_id, booking_id, amount, status) SELECT p.id, p.booking_id, 150000, 'confirmed' FROM payments p WHERE p.booking_id = ?", bookingId);
        entityManager.clear();
        decide(adminToken, capped, "refund-full", NOTE, 200);
        assertThat((BigDecimal) refundOf(capped).get("amount")).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("Tu choi: ghi chu 5-1000 ky tu, khong yeu cau hoan tien, chua giai trinh, chua co thanh toan da xac nhan, da hoan het, khong co quyen, khong ton tai")
    void guards() throws Exception {
        String id = reviewing(60000);
        decide(adminToken, id, "refund-full", null, 400);
        decide(adminToken, id, "refund-full", "abcd", 400);
        decide(adminToken, id, "refund-full", "x".repeat(1001), 400);
        decide(playerToken, id, "refund-full", NOTE, 403);
        decide(ownerToken, id, "refund-half", NOTE, 403);
        decide(adminToken, UUID.randomUUID().toString(), "refund-full", NOTE, 404);

        String plain = reviewing(0);
        decide(adminToken, plain, "refund-full", NOTE, 409);

        UUID bookingId = booking(playerToken, BookingStatus.COMPLETED, 2);
        String waiting = dispute(playerToken, bookingId, 201, "other", "Chu san thu them tien thue vot ngoai thoa thuan").get("id").asText();
        decide(adminToken, waiting, "refund-full", NOTE, 409);

        String unpaid = reviewing(0);
        jdbc.update("UPDATE booking_disputes SET refund_requested = true, requested_refund_amount = 50000 WHERE id = ?", UUID.fromString(unpaid));
        jdbc.update("UPDATE payments SET status = 'payer_reported' WHERE booking_id = (SELECT booking_id FROM booking_disputes WHERE id = ?)", UUID.fromString(unpaid));
        entityManager.clear();
        decide(adminToken, unpaid, "refund-full", NOTE, 409);

        String all = reviewing(60000);
        UUID allBooking = jdbc.queryForObject("SELECT booking_id FROM booking_disputes WHERE id = ?", UUID.class, UUID.fromString(all));
        jdbc.update("INSERT INTO refunds (payment_id, booking_id, amount, status) SELECT p.id, p.booking_id, 180000, 'confirmed' FROM payments p WHERE p.booking_id = ?", allBooking);
        entityManager.clear();
        decide(adminToken, all, "refund-half", NOTE, 409);

        assertThat(jdbc.queryForObject("SELECT status FROM booking_disputes WHERE id = ?", String.class, UUID.fromString(id))).isEqualTo("reviewing");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refunds WHERE dispute_id = ?", Integer.class, UUID.fromString(id))).isZero();
    }
}
