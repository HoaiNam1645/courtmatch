package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;

import com.courtly.common.enums.BookingStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.87 - danh dau yeu cau hoan tien khi tao bao cao. */
class BookingDisputeRefundTest extends BookingDisputeTestBase {

    private static final String DESCRIPTION = "Bi thu them tien nuoc ngoai gia niem yet cua don";

    private UUID paidBooking() throws Exception {
        UUID id = booking(playerToken, BookingStatus.COMPLETED, 1);
        payment(id, "confirmed");
        return id;
    }

    private int payable(UUID bookingId) {
        return jdbc.queryForObject("SELECT (total_amount - discount_amount)::int FROM bookings WHERE id = ?", Integer.class, bookingId);
    }

    @Test
    @DisplayName("Khong tick: chi la phan anh, khong luu so tien hoan; tick khong nhap tien: lay toan bo so da tra")
    void refundIsOptionalAndDefaultsToTheFullAmount() throws Exception {
        UUID plain = paidBooking();
        JsonNode noRefund = dispute(playerToken, plain, 201, "extra_charge", DESCRIPTION);
        assertThat(noRefund.get("refundRequested").asBoolean()).isFalse();
        assertThat(noRefund.has("refundAmount")).isFalse();
        dispute(playerToken, plain, 409, "extra_charge", DESCRIPTION, "\"refundRequested\":true"); // da co bao cao dang xu ly

        UUID full = paidBooking();
        JsonNode result = dispute(playerToken, full, 201, "extra_charge", DESCRIPTION, "\"refundRequested\":true");
        assertThat(result.get("refundRequested").asBoolean()).isTrue();
        assertThat(result.get("refundAmount").asInt()).isEqualTo(payable(full));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT requested_refund_amount::int FROM booking_disputes WHERE booking_id = ? AND refund_requested",
                Integer.class, full)).isEqualTo(payable(full));
    }

    @Test
    @DisplayName("Tick kem so tien tuy chon trong khoang 1.000d toi so da tra (sau voucher); de lam tron 2 chu so")
    void customAmountWithinTheRange() throws Exception {
        UUID id = paidBooking();
        JsonNode result = dispute(playerToken, id, 201, "extra_charge", DESCRIPTION, "\"refundRequested\":true,\"refundAmount\":50000.499");
        assertThat(result.get("refundAmount").asDouble()).isEqualTo(50000.50);

        UUID exact = paidBooking();
        dispute(playerToken, exact, 201, "venue_closed", DESCRIPTION, "\"refundRequested\":true,\"refundAmount\":" + payable(exact));
        UUID min = paidBooking();
        dispute(playerToken, min, 201, "venue_closed", DESCRIPTION, "\"refundRequested\":true,\"refundAmount\":1000");
    }

    @Test
    @DisplayName("Voucher: so tien toi da la so nguoi choi thuc tra (sau giam), khong phai gia san")
    void maximumIsTheAmountActuallyPaid() throws Exception {
        UUID id = paidBooking();
        jdbc.update("UPDATE bookings SET discount_amount = 30000 WHERE id = ?", id);
        entityManager.clear();
        assertThat(payable(id)).isEqualTo(150000);
        assertThat(dispute(playerToken, id, 400, "extra_charge", DESCRIPTION, "\"refundRequested\":true,\"refundAmount\":150001")
                .get("fieldErrors").get("refundAmount").asText()).contains("150000");
        assertThat(dispute(playerToken, id, 201, "extra_charge", DESCRIPTION, "\"refundRequested\":true").get("refundAmount").asInt()).isEqualTo(150000);
    }

    @Test
    @DisplayName("So tien ngoai khoang, am, bang 0 hoac thap hon 1.000d bi 400 va khong luu bao cao")
    void rejectsAmountsOutOfRange() throws Exception {
        UUID id = paidBooking();
        for (String amount : new String[] {"999", "0", "-5000", "180001", "99999999"}) {
            JsonNode error = dispute(playerToken, id, 400, "extra_charge", DESCRIPTION, "\"refundRequested\":true,\"refundAmount\":" + amount);
            assertThat(error.get("fieldErrors").has("refundAmount")).as(amount).isTrue();
        }
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_disputes WHERE booking_id = ?", Integer.class, id)).isZero();
    }

    @Test
    @DisplayName("Co so tien nhung khong tick yeu cau hoan tien bi 400")
    void amountWithoutTick() throws Exception {
        UUID id = paidBooking();
        assertThat(dispute(playerToken, id, 400, "extra_charge", DESCRIPTION, "\"refundAmount\":50000").get("fieldErrors").has("refundAmount")).isTrue();
        assertThat(dispute(playerToken, id, 400, "extra_charge", DESCRIPTION, "\"refundRequested\":false,\"refundAmount\":50000")
                .get("fieldErrors").has("refundAmount")).isTrue();
        dispute(playerToken, id, 201, "extra_charge", DESCRIPTION, "\"refundRequested\":false");
    }

    @Test
    @DisplayName("Chi tick duoc khi chu san da xac nhan nhan tien: chua co thanh toan hoac thanh toan chua xac nhan bi 400")
    void requiresAConfirmedPayment() throws Exception {
        UUID noPayment = booking(playerToken, BookingStatus.COMPLETED, 1);
        assertThat(dispute(playerToken, noPayment, 400, "extra_charge", DESCRIPTION, "\"refundRequested\":true")
                .get("fieldErrors").has("refundRequested")).isTrue();
        for (String status : new String[] {"awaiting_payment", "payer_reported", "rejected", "expired"}) {
            UUID id = booking(playerToken, BookingStatus.COMPLETED, 1);
            payment(id, status);
            assertThat(dispute(playerToken, id, 400, "extra_charge", DESCRIPTION, "\"refundRequested\":true").get("fieldErrors").has("refundRequested"))
                    .as(status).isTrue();
        }
        // Van gui duoc nhu mot phan anh khong doi hoan tien.
        dispute(playerToken, noPayment, 201, "extra_charge", DESCRIPTION);
    }

    @Test
    @DisplayName("Tick chi ghi nhan yeu cau: khong tao ban ghi hoan tien, khong doi trang thai don, khong dong toi hoa hong; gan payment cua don")
    void onlyRecordsTheRequest() throws Exception {
        UUID id = paidBooking();
        long refundsBefore = jdbc.queryForObject("SELECT count(*) FROM refunds", Long.class);
        long commissionsBefore = jdbc.queryForObject("SELECT count(*) FROM booking_commissions", Long.class);
        String disputeId = dispute(playerToken, id, 201, "extra_charge", DESCRIPTION, "\"refundRequested\":true,\"refundAmount\":60000").get("id").asText();
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refunds", Long.class)).isEqualTo(refundsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking_commissions", Long.class)).isEqualTo(commissionsBefore);
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, id)).isEqualTo("completed");
        assertThat(jdbc.queryForObject("SELECT payment_id IS NOT NULL FROM booking_disputes WHERE id = ?::uuid", Boolean.class, disputeId)).isTrue();
    }
}
