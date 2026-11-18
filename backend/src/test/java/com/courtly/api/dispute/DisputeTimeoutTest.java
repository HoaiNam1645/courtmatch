package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;

import com.courtly.service.dispute.DisputeTimeoutService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 2.3.107 - tu xu thua khi chu san qua han giai trinh, nhac o gio thu 24; 2.3.104 - tu dong tam ngung san bi xu thua nhieu lan. */
class DisputeTimeoutTest extends DisputeFinanceTestBase {

    @Autowired private DisputeTimeoutService timeoutService;

    private String report(UUID bookingId, String extra) throws Exception {
        return dispute(playerToken, bookingId, 201, "venue_closed", "San dong cua khi toi den luc 18h, khong ai mo cong", extra).get("id").asText();
    }

    private void overdue(String disputeId, String createdAgo) {
        jdbc.update("UPDATE booking_disputes SET created_at = now() - interval '" + createdAgo + "', owner_response_deadline = now() - interval '1 minute' WHERE id = ?", UUID.fromString(disputeId));
        entityManager.clear();
    }

    private String field(String disputeId, String column) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT " + column + "::text FROM booking_disputes WHERE id = ?", String.class, UUID.fromString(disputeId));
    }

    @Test
    @DisplayName("Qua han + co yeu cau hoan tien: tu quyet dinh hoan toan phan, tao khoan hoan, hoa hong van tam hoan, handled_by trong, thong bao hai ben")
    void overdueWithRefundRequest() throws Exception {
        UUID bookingId = completed(3);
        payment(bookingId, "confirmed");
        String id = report(bookingId, "\"refundRequested\":true,\"refundAmount\":120000");
        overdue(id, "49 hours");

        assertThat(timeoutService.resolveOverdue(Instant.now())).isEqualTo(1);
        assertThat(field(id, "status || '|' || resolution")).isEqualTo("resolved|refund_full");
        assertThat(field(id, "handled_by")).isNull();
        assertThat(jdbc.queryForObject("SELECT amount || '|' || status FROM refunds WHERE dispute_id = ?", String.class, UUID.fromString(id))).isEqualTo("120000.00|awaiting_owner_refund");
        assertThat(commissionStatus(bookingId)).isEqualTo("suspended");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE type = 'booking_dispute_resolved' AND data_json ->> 'disputeId' = ?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'dispute.auto_resolve' AND entity_id = ?", Integer.class, UUID.fromString(id))).isEqualTo(1);
        JsonNode timeline = getJson("/api/v1/booking-disputes/" + id, playerToken).get("timeline");
        assertThat(timeline.get(timeline.size() - 1).get("actor").asText()).isEqualTo("system");
        assertThat(timeoutService.resolveOverdue(Instant.now())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refunds WHERE dispute_id = ?", Integer.class, UUID.fromString(id))).isEqualTo(1);
    }

    @Test
    @DisplayName("Qua han + chi phan anh: ket luan owner_no_response, khong co khoan hoan, hoa hong khoi phuc; da giai trinh hoac chua qua han thi khong dong toi")
    void overdueWithoutRefundAndGuards() throws Exception {
        UUID plainBooking = completed(3);
        String plain = report(plainBooking, null);
        overdue(plain, "49 hours");
        UUID answeredBooking = completed(4);
        String answered = report(answeredBooking, null);
        overdue(answered, "49 hours");
        jdbc.update("UPDATE booking_disputes SET owner_response = 'Da giai trinh', status = 'reviewing' WHERE id = ?", UUID.fromString(answered));
        UUID freshBooking = completed(5);
        String fresh = report(freshBooking, null);
        entityManager.clear();

        assertThat(timeoutService.resolveOverdue(Instant.now())).isEqualTo(1);
        assertThat(field(plain, "status || '|' || resolution")).isEqualTo("resolved|owner_no_response");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refunds WHERE dispute_id = ?", Integer.class, UUID.fromString(plain))).isZero();
        assertThat(commissionStatus(plainBooking)).isEqualTo("due");
        assertThat(field(answered, "status")).isEqualTo("reviewing");
        assertThat(field(fresh, "status")).isEqualTo("awaiting_owner_response");
        assertThat(commissionStatus(freshBooking)).isEqualTo("suspended");
    }

    @Test
    @DisplayName("Nhac o gio thu 24: chi bao cao du 24 gio va con han, moi bao cao mot lan")
    void remindsOnce() throws Exception {
        UUID dueBooking = completed(3);
        String due = report(dueBooking, null);
        jdbc.update("UPDATE booking_disputes SET created_at = now() - interval '25 hours', owner_response_deadline = now() + interval '23 hours' WHERE id = ?", UUID.fromString(due));
        UUID youngBooking = completed(4);
        String young = report(youngBooking, null);
        jdbc.update("UPDATE booking_disputes SET created_at = now() - interval '10 hours' WHERE id = ?", UUID.fromString(young));
        entityManager.clear();

        assertThat(timeoutService.remind(Instant.now())).isEqualTo(1);
        assertThat(timeoutService.remind(Instant.now())).isZero();
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE type = 'booking_dispute_reminder' AND user_id = ? AND data_json ->> 'disputeId' = ?", Integer.class, owner.getId(), due)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE type = 'booking_dispute_reminder' AND data_json ->> 'disputeId' = ?", Integer.class, young)).isZero();
        assertThat(field(due, "owner_reminded_at")).isNotNull();
    }

    private String venueStatus() {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM venues WHERE id = ?", String.class, venue.getId());
    }

    @Test
    @DisplayName("Du 3 lan xu thua trong 90 ngay thi tu dong tam ngung san (khong tinh bac bo/huy); bao chu san; admin bat lai kem ghi chu; chi tam ngung lai khi co lan xu thua moi")
    void autoSuspendsAndReactivates() throws Exception {
        for (int i = 0; i < 2; i++) {
            String id = report(completed(i + 1), null);
            overdue(id, "49 hours");
        }
        // Mot bao cao bi bac bo khong tinh.
        String rejected = report(completed(3), null);
        answer(rejected);
        adminPost("/api/v1/admin/booking-disputes/" + rejected + "/reject", "{\"note\":\"Khong du bang chung\"}", 200);
        timeoutService.resolveOverdue(Instant.now());
        assertThat(venueStatus()).isEqualTo("active");

        String third = report(completed(4), null);
        overdue(third, "49 hours");
        timeoutService.resolveOverdue(Instant.now());
        assertThat(venueStatus()).isEqualTo("inactive");
        assertThat(jdbc.queryForObject("SELECT suspension_reason FROM venues WHERE id = ?", String.class, venue.getId())).contains("3 báo cáo");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE type = 'venue_suspended' AND user_id = ?", Integer.class, owner.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'venue.auto_suspend' AND entity_id = ?", Integer.class, venue.getId())).isEqualTo(1);

        adminPost("/api/v1/admin/venues/" + venue.getId() + "/reactivate", "{}", 400);
        adminPost("/api/v1/admin/venues/" + venue.getId() + "/reactivate", "{\"note\":\"Da lam viec voi chu san\"}", 200);
        assertThat(venueStatus()).isEqualTo("active");
        adminPost("/api/v1/admin/venues/" + venue.getId() + "/reactivate", "{\"note\":\"Da lam viec voi chu san\"}", 409);

        // Chua co lan xu thua moi nen danh gia lai khong tam ngung; mot lan xu thua moi thi tam ngung lai.
        timeoutService.resolveOverdue(Instant.now());
        assertThat(venueStatus()).isEqualTo("active");
        String fourth = report(completed(5), null);
        overdue(fourth, "49 hours");
        timeoutService.resolveOverdue(Instant.now());
        assertThat(venueStatus()).isEqualTo("inactive");
    }

    @Test
    @DisplayName("Admin quyet dinh hoan tien cung duoc tinh la xu thua; san da inactive hoac chi 2 lan thi khong tam ngung")
    void adminLossesCount() throws Exception {
        for (int i = 0; i < 3; i++) {
            UUID bookingId = completed(i + 1);
            payment(bookingId, "confirmed");
            String id = report(bookingId, "\"refundRequested\":true,\"refundAmount\":50000");
            answer(id);
            adminPost("/api/v1/admin/booking-disputes/" + id + "/" + (i == 1 ? "refund-half" : "refund-full"), "{\"note\":\"Camera cho thay san dong cua\"}", 200);
            assertThat(venueStatus()).isEqualTo(i < 2 ? "active" : "inactive");
        }
    }
}
