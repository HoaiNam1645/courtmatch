package com.courtly.api.dispute;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Nen cho test luong tien cua bao cao: chinh sach hoa hong co dinh 20.000d, admin, don da hoan thanh co hoa hong. */
abstract class DisputeFinanceTestBase extends BookingDisputeTestBase {

    @Autowired private PlatformFeeConfigRepository feeConfigRepository;
    @Autowired protected CommissionService commissionService;

    protected User admin;
    protected String adminToken;

    @BeforeEach
    void setUpFinance() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("finance-admin");
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

    /** Don da choi xong co hoa hong 20.000d (due). */
    protected UUID completed(int endedDaysAgo) throws Exception {
        UUID id = booking(playerToken, BookingStatus.COMPLETED, endedDaysAgo);
        Booking booking = bookingRepository.findById(id).orElseThrow();
        booking.setCompletedAt(Instant.now());
        bookingRepository.saveAndFlush(booking);
        commissionService.createForCompletedBooking(booking, Instant.now());
        entityManager.flush();
        entityManager.clear();
        return id;
    }

    protected String commissionStatus(UUID bookingId) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM booking_commissions WHERE booking_id = ?", String.class, bookingId);
    }

    protected BigDecimal commissionAmount(UUID bookingId) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT commission_amount FROM booking_commissions WHERE booking_id = ?", BigDecimal.class, bookingId);
    }

    /** Chu san da giai trinh ({@code hoursAgo} gio truoc), bao cao dang cho quan tri vien. */
    protected void answer(String disputeId) {
        jdbc.update("UPDATE booking_disputes SET status = 'reviewing', owner_response = 'Da giai trinh', created_at = now() - interval '2 hours', owner_responded_at = now() - interval '1 hour' WHERE id = ?", UUID.fromString(disputeId));
        entityManager.clear();
    }

    protected JsonNode adminPost(String path, String json, int expected) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(path).header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    protected JsonNode getJson(String url, String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(url).header("Authorization", bearer(token))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /** Bao cao hoan tien da co payment confirmed 180.000d, hoa hong due, da giai trinh; tra ve {disputeId, bookingId}. */
    protected UUID[] refundableDispute(int endedDaysAgo, int requested) throws Exception {
        UUID bookingId = completed(endedDaysAgo);
        payment(bookingId, "confirmed");
        String id = dispute(playerToken, bookingId, 201, "extra_charge", "Chu san thu them tien thue vot ngoai thoa thuan",
                "\"refundRequested\":true,\"refundAmount\":" + requested).get("id").asText();
        answer(id);
        return new UUID[] {UUID.fromString(id), bookingId};
    }
}
