package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.User;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.98 - danh sach bao cao cho quan tri vien; 2.3.99 - chi tiet kem bang chung hai phia. */
class AdminDisputeViewTest extends BookingDisputeTestBase {

    private static final String PATH = "/api/v1/admin/booking-disputes";
    private static final String DESCRIPTION = "San dong cua khi toi den luc 18h, khong ai mo cong";

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUpAdmin() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("dispute-admin");
        adminToken = loginAs(admin);
    }

    private JsonNode call(String token, String path, int expected) throws Exception {
        var request = get(PATH + path);
        if (token != null) request = request.header("Authorization", bearer(token));
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private String report(int endedDaysAgo, String category, String extra) throws Exception {
        UUID bookingId = booking(playerToken, BookingStatus.COMPLETED, endedDaysAgo);
        return dispute(playerToken, bookingId, 201, category, DESCRIPTION, extra).get("id").asText();
    }

    private void set(String id, String sql) {
        jdbc.update("UPDATE booking_disputes SET " + sql + " WHERE id = ?", UUID.fromString(id));
        entityManager.clear();
    }

    /** Chu san da giai trinh {@code hoursAgo} gio truoc (bao cao gui truoc do 1 gio; cac moc phai khac nhau vi test chay trong mot transaction). */
    private void answered(String id, int hoursAgo) {
        set(id, "status = 'reviewing', owner_response = 'Da giai trinh', created_at = now() - interval '" + (hoursAgo + 1)
                + " hours', owner_responded_at = now() - interval '" + hoursAgo + " hours'");
    }

    @Test
    @DisplayName("Danh sach: mac dinh la cho xu ly (chu san da giai trinh), cu nhat len dau; co bo dem, so anh, so bao cao truoc cua san")
    void pendingFirst() throws Exception {
        String old = report(3, "venue_closed", null);
        String recent = report(2, "extra_charge", null);
        String waiting = report(1, "other", null);
        answered(old, 5);
        answered(recent, 2);
        set(old, "reporter_evidences_json = '[{\"id\":\"a\"},{\"id\":\"b\"}]'::jsonb, owner_evidences_json = '[{\"id\":\"c\"}]'::jsonb");

        JsonNode result = call(adminToken, "", 200);
        assertThat(result.get("items")).hasSize(2);
        assertThat(result.get("items").get(0).get("id").asText()).isEqualTo(old);
        assertThat(result.get("items").get(0).get("playerImages").asInt()).isEqualTo(2);
        assertThat(result.get("items").get(0).get("ownerImages").asInt()).isEqualTo(1);
        assertThat(result.get("items").get(1).get("id").asText()).isEqualTo(recent);
        assertThat(result.get("items").get(1).get("priorVenueDisputes").asLong()).isEqualTo(1);
        assertThat(result.get("items").get(0).get("ownerName").asText()).isNotBlank();
        JsonNode counts = result.get("counts");
        assertThat(counts.get("pending").asLong()).isEqualTo(2);
        assertThat(counts.get("awaiting").asLong()).isEqualTo(1);
        assertThat(counts.get("all").asLong()).isEqualTo(3);

        JsonNode awaiting = call(adminToken, "?status=awaiting", 200);
        assertThat(awaiting.get("items")).hasSize(1);
        assertThat(awaiting.get("items").get(0).get("id").asText()).isEqualTo(waiting);
        assertThat(call(adminToken, "?status=all", 200).get("items")).hasSize(3);
        assertThat(call(adminToken, "?status=resolved", 200).get("items")).isEmpty();
    }

    @Test
    @DisplayName("Danh sach: tim theo ma don/ten nguoi choi/ten san, loc theo loai, hoan tien, khoang ngay; tham so sai bi 400")
    void filters() throws Exception {
        String refund = report(1, "venue_closed", null);
        report(2, "other", null);
        jdbc.update("UPDATE booking_disputes SET status = 'reviewing'");
        set(refund, "refund_requested = true, requested_refund_amount = 50000");
        String code = jdbc.queryForObject("SELECT b.booking_code FROM bookings b JOIN booking_disputes d ON d.booking_id = b.id WHERE d.id = ?", String.class, UUID.fromString(refund));

        assertThat(call(adminToken, "?q=" + code, 200).get("items")).hasSize(1);
        assertThat(call(adminToken, "?q=Bao", 200).get("items")).hasSize(2);
        assertThat(call(adminToken, "?q=khong-co-ai", 200).get("items")).isEmpty();
        assertThat(call(adminToken, "?category=venue_closed", 200).get("items")).hasSize(1);
        assertThat(call(adminToken, "?refund=yes", 200).get("items")).hasSize(1);
        assertThat(call(adminToken, "?refund=no", 200).get("items")).hasSize(1);
        assertThat(call(adminToken, "?from=2020-01-01&to=2020-01-31", 200).get("items")).isEmpty();
        call(adminToken, "?status=bad", 400);
        call(adminToken, "?from=2026-02-01&to=2026-01-01", 400);
        call(adminToken, "?size=0", 400);
        assertThat(call(adminToken, "?size=1&page=1", 200).get("totalPages").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Chi tiet: hai ben, thanh toan va so da hoan, hoa hong, lich su san/nguoi choi, so tien hoan toan phan va mot nua")
    void detail() throws Exception {
        UUID bookingId = booking(playerToken, BookingStatus.COMPLETED, 1);
        payment(bookingId, "confirmed");
        String id = dispute(playerToken, bookingId, 201, "extra_charge", DESCRIPTION, "\"refundRequested\":true,\"refundAmount\":125000").get("id").asText();
        answered(id, 1);
        jdbc.update("UPDATE booking_disputes SET reporter_evidences_json = '[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"originalName\":\"a.png\",\"size\":10,\"contentType\":\"image/png\",\"uploadedAt\":\"2026-01-01T00:00:00Z\",\"supplementId\":null}]'::jsonb WHERE id = ?", UUID.fromString(id));
        jdbc.update("INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, due_at) VALUES (?, ?, 180000, 'fixed', 20000, 20000, 'due', now())", bookingId, owner.getId());
        entityManager.clear();

        JsonNode detail = call(adminToken, "/" + id, 200);
        assertThat(detail.get("status").asText()).isEqualTo("reviewing");
        assertThat(detail.get("canDecide").asBoolean()).isTrue();
        assertThat(detail.get("canRefund").asBoolean()).isTrue();
        assertThat(detail.get("player").get("fullName").asText()).isNotBlank();
        assertThat(detail.get("owner").get("email").asText()).isNotBlank();
        assertThat(detail.get("money").get("confirmedPaid").decimalValue()).isEqualByComparingTo("180000");
        assertThat(detail.get("money").get("refundable").decimalValue()).isEqualByComparingTo("180000");
        assertThat(detail.get("money").get("payments")).hasSize(1);
        assertThat(detail.get("commission").get("amount").decimalValue()).isEqualByComparingTo("20000");
        assertThat(detail.get("fullAmount").decimalValue()).isEqualByComparingTo("125000");
        assertThat(detail.get("halfAmount").decimalValue()).isEqualByComparingTo("62000");
        assertThat(detail.get("history").get("venueDisputes").asLong()).isEqualTo(1);
        assertThat(detail.get("history").get("reporterDisputes").asLong()).isEqualTo(1);
        assertThat(detail.get("timeline").get(0).get("actor").asText()).isEqualTo("player");
        assertThat(detail.get("timeline").get(0).get("evidences")).hasSize(1);
        assertThat(detail.get("timeline").get(1).get("actor").asText()).isEqualTo("owner");

        // Da hoan truoc do thi so con hoan duoc giam, toan phan khong vuot so do.
        jdbc.update("INSERT INTO refunds (payment_id, booking_id, amount, status) SELECT p.id, p.booking_id, 100000, 'confirmed' FROM payments p WHERE p.booking_id = ?", bookingId);
        entityManager.clear();
        JsonNode after = call(adminToken, "/" + id, 200);
        assertThat(after.get("money").get("refundedBefore").decimalValue()).isEqualByComparingTo("100000");
        assertThat(after.get("fullAmount").decimalValue()).isEqualByComparingTo("80000");
        assertThat(after.get("halfAmount").decimalValue()).isEqualByComparingTo("62000");
    }

    @Test
    @DisplayName("Chi tiet: khong yeu cau hoan tien hoac chua giai trinh thi khong co nut quyet dinh hoan tien; khong co quyen / khong ton tai")
    void guards() throws Exception {
        String plain = report(1, "other", null);
        answered(plain, 1);
        JsonNode noRefund = call(adminToken, "/" + plain, 200);
        assertThat(noRefund.get("canDecide").asBoolean()).isTrue();
        assertThat(noRefund.get("canRefund").asBoolean()).isFalse();
        assertThat(noRefund.has("fullAmount")).isFalse();

        String awaiting = report(2, "other", null);
        assertThat(call(adminToken, "/" + awaiting, 200).get("canDecide").asBoolean()).isFalse();

        call(adminToken, "/" + UUID.randomUUID(), 404);
        call(null, "", 401);
        call(playerToken, "", 403);
        call(ownerToken, "/" + plain, 403);
    }
}
