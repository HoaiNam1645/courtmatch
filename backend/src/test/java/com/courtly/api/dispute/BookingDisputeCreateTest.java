package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 2.3.85 - tao bao cao ve don dat san; 2.3.86 - loai bao cao theo danh muc. */
class BookingDisputeCreateTest extends BookingDisputeTestBase {

    private static final String DESCRIPTION = "San dong cua khi toi den luc 18h, khong ai mo cong";

    private JsonNode detail(String token, UUID bookingId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("2.3.86 Danh muc: dung nam loai co dinh, moi loai co nhan, goi y va so ky tu mo ta toi thieu (ly do khac 20)")
    void listsTheCategories() throws Exception {
        JsonNode categories = objectMapper.readTree(mockMvc.perform(get("/api/v1/booking-disputes/categories")
                        .header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(categories).hasSize(5);
        assertThat(categories.findValuesAsText("code")).containsExactly("venue_closed", "venue_mismatch", "owner_cancelled", "extra_charge", "other");
        assertThat(categories.get(0).get("label").asText()).isEqualTo("Sân đóng cửa");
        categories.forEach(item -> assertThat(item.get("hint").asText()).isNotBlank());
        assertThat(categories.get(0).get("minDescriptionLength").asInt()).isEqualTo(10);
        assertThat(categories.get(4).get("minDescriptionLength").asInt()).isEqualTo(20);
        mockMvc.perform(get("/api/v1/booking-disputes/categories")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Tao bao cao: awaiting_owner_response, han giai trinh 48 gio, luu DB, chua doi trang thai don; bao cho chu san")
    void createsADispute() throws Exception {
        UUID bookingId = booking(playerToken, BookingStatus.COMPLETED, 1);
        Instant before = Instant.now();
        JsonNode created = dispute(playerToken, bookingId, 201, "venue_closed", "  " + DESCRIPTION + "  ");
        assertThat(created.get("status").asText()).isEqualTo("awaiting_owner_response");
        assertThat(created.get("category").asText()).isEqualTo("venue_closed");
        assertThat(created.get("categoryLabel").asText()).isEqualTo("Sân đóng cửa");
        assertThat(created.get("description").asText()).isEqualTo(DESCRIPTION);
        assertThat(created.get("bookingId").asText()).isEqualTo(bookingId.toString());
        Instant deadline = Instant.parse(created.get("ownerResponseDeadline").asText());
        assertThat(Duration.between(before.plus(Duration.ofHours(48)), deadline).abs()).isLessThan(Duration.ofMinutes(2));

        entityManager.flush();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM booking_disputes WHERE id = ?::uuid AND booking_id = ? AND reporter_id = ? AND category = 'venue_closed'
                  AND status = 'awaiting_owner_response' AND refund_requested = false AND requested_refund_amount IS NULL
                """, Integer.class, created.get("id").asText(), bookingId, player.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId)).isEqualTo("completed");

        JsonNode data = objectMapper.readTree(jdbc.queryForObject(
                "SELECT data_json::text FROM notifications WHERE user_id = ? AND type = 'booking_dispute_opened'", String.class, owner.getId()));
        assertThat(data.get("disputeId").asText()).isEqualTo(created.get("id").asText());
        assertThat(data.get("route").asText()).isEqualTo("/owner/bookings");
        assertThat(jdbc.queryForObject("SELECT content FROM notifications WHERE user_id = ? AND type = 'booking_dispute_opened'",
                String.class, owner.getId())).contains(created.get("bookingCode").asText()).contains("Sân đóng cửa");
    }

    @Test
    @DisplayName("Chi don da xac nhan hoac hoan thanh moi bao cao duoc; moi trang thai khac tra 409 BOOKING_DISPUTE_INVALID")
    void onlyConfirmedOrCompletedBookings() throws Exception {
        dispute(playerToken, booking(playerToken, BookingStatus.CONFIRMED, 1), 201, "extra_charge", "Bi thu them 50.000d tien nuoc");
        for (BookingStatus status : new BookingStatus[] {BookingStatus.PENDING_OWNER_CONFIRMATION, BookingStatus.AWAITING_PAYMENT,
                BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.EXPIRED, BookingStatus.DISPUTED, BookingStatus.REFUNDED}) {
            JsonNode error = dispute(playerToken, booking(playerToken, status, 1), 409, "venue_closed", DESCRIPTION);
            assertThat(error.get("code").asText()).isEqualTo("BOOKING_DISPUTE_INVALID");
        }
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_disputes WHERE reporter_id = ?", Integer.class, player.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Chi trong 7 ngay ke tu gio ket thuc: 6 ngay truoc con duoc, 8 ngay truoc qua han; don chua choi van bao cao duoc")
    void respectsTheReportWindow() throws Exception {
        dispute(playerToken, booking(playerToken, BookingStatus.COMPLETED, 6), 201, "venue_mismatch", "Mat san khong giong anh mo ta");
        JsonNode late = dispute(playerToken, booking(playerToken, BookingStatus.COMPLETED, 8), 409, "venue_mismatch", "Mat san khong giong anh mo ta");
        assertThat(late.get("message").asText()).contains("7 ngay");
        dispute(playerToken, booking(playerToken, BookingStatus.CONFIRMED, -1), 201, "owner_cancelled", "Chu san nhan tin bao huy lich");
    }

    @Test
    @DisplayName("Moi don mot bao cao dang xu ly; dong bao cao cu thi bao cao lai duoc")
    void oneActiveDisputePerBooking() throws Exception {
        UUID bookingId = booking(playerToken, BookingStatus.COMPLETED, 1);
        String first = dispute(playerToken, bookingId, 201, "venue_closed", DESCRIPTION).get("id").asText();
        JsonNode again = dispute(playerToken, bookingId, 409, "extra_charge", "Bi thu them tien ngoai gia niem yet");
        assertThat(again.get("code").asText()).isEqualTo("BOOKING_DISPUTE_INVALID");
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_disputes WHERE booking_id = ?", Integer.class, bookingId)).isEqualTo(1);

        for (String closed : new String[] {"resolved", "rejected", "cancelled"}) {
            jdbc.update("UPDATE booking_disputes SET status = ? WHERE id = ?::uuid", closed, first);
            entityManager.clear();
            first = dispute(playerToken, bookingId, 201, "extra_charge", "Bi thu them tien ngoai gia niem yet").get("id").asText();
        }
    }

    @Test
    @DisplayName("Loai ngoai danh muc, mo ta ngan hon muc toi thieu cua loai do (khac: 20), qua dai hoac trong deu bi 400")
    void validatesInput() throws Exception {
        UUID bookingId = booking(playerToken, BookingStatus.COMPLETED, 1);
        assertThat(dispute(playerToken, bookingId, 400, "refund_me", DESCRIPTION).get("fieldErrors").has("category")).isTrue();
        assertThat(dispute(playerToken, bookingId, 400, null, DESCRIPTION).get("fieldErrors").has("category")).isTrue();
        assertThat(dispute(playerToken, bookingId, 400, "venue_closed", "ngan").get("fieldErrors").has("description")).isTrue();
        assertThat(dispute(playerToken, bookingId, 400, "venue_closed", "         ").get("fieldErrors").has("description")).isTrue();
        assertThat(dispute(playerToken, bookingId, 400, "venue_closed", null).get("fieldErrors").has("description")).isTrue();
        assertThat(dispute(playerToken, bookingId, 400, "other", "du muoi ky tu thoi").get("fieldErrors").get("description").asText()).contains("20");
        assertThat(dispute(playerToken, bookingId, 400, "venue_closed", "x".repeat(2001)).get("fieldErrors").has("description")).isTrue();
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_disputes WHERE booking_id = ?", Integer.class, bookingId)).isZero();
        dispute(playerToken, bookingId, 201, "other", "x".repeat(2000));
    }

    @Test
    @DisplayName("Chi nguoi dat bao cao duoc: nguoi khac va id la 404, chu san 404 (khong phai don cua ho), chua dang nhap 401")
    void onlyTheBooker() throws Exception {
        UUID bookingId = booking(playerToken, BookingStatus.COMPLETED, 1);
        dispute(loginAs(persistUser("stranger-dispute", Role.PLAYER)), bookingId, 404, "venue_closed", DESCRIPTION);
        dispute(ownerToken, bookingId, 404, "venue_closed", DESCRIPTION);
        dispute(playerToken, UUID.randomUUID(), 404, "venue_closed", DESCRIPTION);
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/disputes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"venue_closed\",\"description\":\"" + DESCRIPTION + "\"}")).andExpect(status().isUnauthorized());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_disputes WHERE booking_id = ?", Integer.class, bookingId)).isZero();
    }

    @Test
    @DisplayName("Chi tiet don cho nguoi dat biet co bao cao duoc khong, han cuoi va bao cao dang xu ly; don khong hop le khong co han")
    void bookingDetailExposesTheReportState() throws Exception {
        UUID reportable = booking(playerToken, BookingStatus.COMPLETED, 1);
        UUID pending = booking(playerToken, BookingStatus.PENDING_OWNER_CONFIRMATION, 1);

        JsonNode before = detail(playerToken, reportable);
        assertThat(before.get("canReportIssue").asBoolean()).isTrue();
        assertThat(before.get("reportDeadline").asText()).isNotBlank();
        assertThat(before.has("dispute")).isFalse();
        JsonNode notReportable = detail(playerToken, pending);
        assertThat(notReportable.get("canReportIssue").asBoolean()).isFalse();
        assertThat(notReportable.has("reportDeadline")).isFalse();

        String disputeId = dispute(playerToken, reportable, 201, "venue_closed", DESCRIPTION).get("id").asText();
        entityManager.flush();
        entityManager.clear();
        JsonNode after = detail(playerToken, reportable);
        assertThat(after.get("canReportIssue").asBoolean()).isFalse();
        assertThat(after.get("dispute").get("id").asText()).isEqualTo(disputeId);
        assertThat(after.get("dispute").get("status").asText()).isEqualTo("awaiting_owner_response");
        assertThat(after.get("dispute").get("category").asText()).isEqualTo("venue_closed");

        // Qua han thi khong con bao cao duoc.
        UUID expired = booking(playerToken, BookingStatus.COMPLETED, 9);
        assertThat(detail(playerToken, expired).get("canReportIssue").asBoolean()).isFalse();
    }
}
