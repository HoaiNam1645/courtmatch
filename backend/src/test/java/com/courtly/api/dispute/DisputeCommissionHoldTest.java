package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.FeeType;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.finance.PlatformFeeConfig;
import com.courtly.domain.finance.PlatformFeeConfigRepository;
import com.courtly.service.finance.CommissionService;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.102 bac bo; 2.3.105 tam hoan hoa hong; 2.3.106 cong no loai khoan tam hoan; 2.3.110 khoi phuc hoa hong. */
class DisputeCommissionHoldTest extends BookingDisputeTestBase {

    @Autowired private PlatformFeeConfigRepository feeConfigRepository;
    @Autowired private CommissionService commissionService;

    private String adminToken;
    private User admin;

    @BeforeEach
    void setUpFinance() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("commission-admin");
        adminToken = loginAs(admin);
        feeConfigRepository.findFirstByStatusOrderByEffectiveFromDesc(ActiveStatus.ACTIVE).ifPresent(existing -> {
            existing.setStatus(ActiveStatus.INACTIVE);
            feeConfigRepository.saveAndFlush(existing);
        });
        PlatformFeeConfig policy = new PlatformFeeConfig();
        policy.setId(UUID.randomUUID());
        policy.setName("Hoa hong thu nghiem");
        policy.setFeeType(FeeType.FIXED);
        policy.setFeeValue(new BigDecimal("20000"));
        policy.setEffectiveFrom(Instant.now().plusSeconds(5));
        policy.setStatus(ActiveStatus.ACTIVE);
        feeConfigRepository.saveAndFlush(policy);
    }

    /** Don da choi xong co hoa hong 20.000d (trang thai due). */
    private UUID completed(int endedDaysAgo) throws Exception {
        UUID id = booking(playerToken, BookingStatus.COMPLETED, endedDaysAgo);
        Booking booking = bookingRepository.findById(id).orElseThrow();
        booking.setCompletedAt(Instant.now());
        bookingRepository.saveAndFlush(booking);
        commissionService.createForCompletedBooking(booking, Instant.now());
        entityManager.flush();
        entityManager.clear();
        return id;
    }

    private String commissionStatus(UUID bookingId) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM booking_commissions WHERE booking_id = ?", String.class, bookingId);
    }

    private JsonNode json(String url, String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(url).header("Authorization", bearer(token))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void answer(String disputeId) {
        jdbc.update("UPDATE booking_disputes SET status = 'reviewing', owner_response = 'Da giai trinh', created_at = now() - interval '2 hours', owner_responded_at = now() - interval '1 hour' WHERE id = ?", UUID.fromString(disputeId));
        entityManager.clear();
    }

    private JsonNode reject(String disputeId, String note, int expected) throws Exception {
        String body = note == null ? "{}" : "{\"note\":\"" + note + "\"}";
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/admin/booking-disputes/" + disputeId + "/reject")
                .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Co bao cao thi hoa hong cua don chuyen suspended (nho trang thai cu), khong nam trong cong no; huy bao cao thi khoi phuc")
    void holdsAndRestoresOnCancel() throws Exception {
        UUID bookingId = completed(1);
        UUID other = completed(2);
        assertThat(commissionStatus(bookingId)).isEqualTo("due");
        JsonNode debtBefore = json("/api/v1/owner/commission-debt", ownerToken);

        String disputeId = dispute(playerToken, bookingId, 201, "other", "Chu san thu them tien thue vot ngoai thoa thuan").get("id").asText();
        assertThat(commissionStatus(bookingId)).isEqualTo("suspended");
        assertThat(commissionStatus(other)).isEqualTo("due");
        assertThat(jdbc.queryForObject("SELECT status_before FROM booking_commissions WHERE booking_id = ?", String.class, bookingId)).isEqualTo("due");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'commission.suspend' AND new_data ->> 'disputeId' = ?", Integer.class, disputeId)).isEqualTo(1);

        JsonNode suspended = json("/api/v1/owner/commissions/suspended", ownerToken);
        assertThat(suspended.get("items")).hasSize(1);
        assertThat(suspended.get("items").get(0).get("disputeId").asText()).isEqualTo(disputeId);
        assertThat(suspended.get("totalAmount").decimalValue()).isEqualByComparingTo("20000");
        JsonNode debtAfter = json("/api/v1/owner/commission-debt", ownerToken);
        assertThat(debtBefore.get("totalOutstanding").decimalValue().subtract(debtAfter.get("totalOutstanding").decimalValue())).isEqualByComparingTo("20000");

        mockMvc.perform(post("/api/v1/booking-disputes/" + disputeId + "/cancel").header("Authorization", bearer(playerToken))).andExpect(status().isOk());
        assertThat(commissionStatus(bookingId)).isEqualTo("due");
        assertThat(jdbc.queryForObject("SELECT suspended_at IS NULL AND status_before IS NULL FROM booking_commissions WHERE booking_id = ?", Boolean.class, bookingId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE type = 'commission_restored' AND user_id = ?", Integer.class, owner.getId())).isEqualTo(1);
        assertThat(json("/api/v1/owner/commissions/suspended", ownerToken).get("items")).isEmpty();
    }

    @Test
    @DisplayName("Bac bo: hoa hong khoi phuc, han da qua trong luc tam hoan thi den han ngay (khong qua han), thong bao ca hai ben, chi mot lan")
    void rejectRestoresCommission() throws Exception {
        UUID bookingId = completed(1);
        String disputeId = dispute(playerToken, bookingId, 201, "venue_closed", "San dong cua khi toi den luc 18h, khong ai mo cong").get("id").asText();
        answer(disputeId);
        jdbc.update("UPDATE booking_commissions SET due_at = now() - interval '3 days' WHERE booking_id = ?", bookingId);
        entityManager.clear();

        reject(disputeId, null, 400);
        reject(disputeId, "abc", 400);
        JsonNode result = reject(disputeId, "Camera cho thay san van mo cua binh thuong", 200);
        assertThat(result.get("status").asText()).isEqualTo("rejected");
        assertThat(result.get("resolution").asText()).isEqualTo("dismissed");
        assertThat(commissionStatus(bookingId)).isEqualTo("due");
        assertThat(jdbc.queryForObject("SELECT due_at >= now() - interval '1 minute' FROM booking_commissions WHERE booking_id = ?", Boolean.class, bookingId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE type = 'booking_dispute_resolved' AND user_id IN (?, ?)", Integer.class, owner.getId(), player.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'dispute.reject'", Integer.class)).isEqualTo(1);
        reject(disputeId, "Camera cho thay san van mo cua binh thuong", 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE type = 'commission_restored' AND user_id = ?", Integer.class, owner.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Bac bo yeu cau hoan tien co resolution rejected; khong tao khoan hoan; nguoi choi/chu san khong co quyen")
    void rejectRefundRequest() throws Exception {
        UUID bookingId = completed(1);
        payment(bookingId, "confirmed");
        String disputeId = dispute(playerToken, bookingId, 201, "extra_charge", "Chu san thu them tien thue vot ngoai thoa thuan",
                "\"refundRequested\":true,\"refundAmount\":50000").get("id").asText();
        answer(disputeId);
        mockMvc.perform(post("/api/v1/admin/booking-disputes/" + disputeId + "/reject").header("Authorization", bearer(playerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Khong du bang chung\"}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/booking-disputes/" + disputeId + "/reject").header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Khong du bang chung\"}")).andExpect(status().isForbidden());
        JsonNode result = reject(disputeId, "Khong du bang chung cho thay bi thu them tien", 200);
        assertThat(result.get("resolution").asText()).isEqualTo("rejected");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refunds WHERE dispute_id = ?", Integer.class, UUID.fromString(disputeId))).isZero();
        assertThat(result.get("canDecide").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("Hoa hong da gan ky doi soat khong tam hoan; don chua hoan thanh tao hoa hong thi sinh ra o trang thai tam hoan")
    void settledNotHeldAndLateCommission() throws Exception {
        UUID settledBooking = completed(1);
        UUID settlementId = UUID.randomUUID();
        jdbc.update("INSERT INTO commission_settlements (id, owner_id, period_from, period_to, amount_due, status) VALUES (?, ?, current_date - 30, current_date, 20000, 'open')", settlementId, owner.getId());
        jdbc.update("UPDATE booking_commissions SET settlement_id = ? WHERE booking_id = ?", settlementId, settledBooking);
        entityManager.clear();
        dispute(playerToken, settledBooking, 201, "other", "Chu san thu them tien thue vot ngoai thoa thuan");
        assertThat(commissionStatus(settledBooking)).isEqualTo("due");

        UUID confirmed = booking(playerToken, BookingStatus.CONFIRMED, 1);
        dispute(playerToken, confirmed, 201, "other", "Chu san thu them tien thue vot ngoai thoa thuan");
        Booking booking = bookingRepository.findById(confirmed).orElseThrow();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(Instant.now());
        bookingRepository.saveAndFlush(booking);
        commissionService.createForCompletedBooking(booking, Instant.now());
        assertThat(commissionStatus(confirmed)).isEqualTo("suspended");
    }
}
