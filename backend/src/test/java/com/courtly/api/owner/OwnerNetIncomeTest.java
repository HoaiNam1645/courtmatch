package com.courtly.api.owner;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.FeeType;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.finance.PlatformFeeConfig;
import com.courtly.domain.finance.PlatformFeeConfigRepository;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import com.courtly.service.finance.CommissionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** 2.3.19 - Xem so tien thuc nhan cua booking. */
class OwnerNetIncomeTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/owner/net-income";

    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private BookingCommissionRepository commissionRepository;
    @Autowired private PlatformFeeConfigRepository feeConfigRepository;
    @Autowired private CommissionService commissionService;
    @Autowired private JdbcTemplate jdbc;

    private User owner;
    private String ownerToken;
    private Venue venue;
    private Court court;
    private String playerToken;
    private int slot;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-net");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Thuc Nhan");
        court = venue.getCourts().getFirst();
        playerToken = loginAs(persistUser("player", Role.PLAYER));
        feeConfigRepository.findFirstByStatusOrderByEffectiveFromDesc(ActiveStatus.ACTIVE).ifPresent(existing -> {
            existing.setStatus(ActiveStatus.INACTIVE);
            feeConfigRepository.saveAndFlush(existing);
        });
        PlatformFeeConfig policy = new PlatformFeeConfig();
        policy.setId(UUID.randomUUID());
        policy.setName("Hoa hong thu nghiem");
        policy.setFeeType(FeeType.PERCENTAGE);
        policy.setFeeValue(new BigDecimal("12.50"));
        policy.setEffectiveFrom(Instant.now().plusSeconds(5));
        policy.setStatus(ActiveStatus.ACTIVE);
        feeConfigRepository.saveAndFlush(policy);
        slot = 0;
    }

    @Test
    @DisplayName("Thuc nhan = gop - hoa hong - tien da hoan (da xac nhan); khoan hoan chua xac nhan khong bi tru")
    void netFormula() throws Exception {
        Payment payment = collect();
        recordCommission(payment);
        refund(payment, "confirmed", "50000");
        refund(payment, "approved", "30000");
        refund(payment, "rejected", "99000");

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].grossAmount").value(180000))
                .andExpect(jsonPath("$.content[0].commissionAmount").value(22500))
                .andExpect(jsonPath("$.content[0].commissionEstimated").value(false))
                .andExpect(jsonPath("$.content[0].commissionStatus").value("due"))
                .andExpect(jsonPath("$.content[0].refundedAmount").value(50000))
                .andExpect(jsonPath("$.content[0].pendingRefundAmount").value(30000))
                .andExpect(jsonPath("$.content[0].netAmount").value(107500))
                .andExpect(jsonPath("$.totals.netAmount").value(107500))
                .andExpect(jsonPath("$.totals.bookings").value(1));
    }

    @Test
    @DisplayName("Chua ghi nhan hoa hong thi dung muc uoc tinh va danh dau; hoa hong duoc mien tinh bang 0")
    void estimatedAndWaivedCommission() throws Exception {
        Payment estimated = collect();
        Payment waived = collect();
        recordCommission(waived);
        BookingCommission commission = commissionRepository.findByBookingId(waived.getBooking().getId()).orElseThrow();
        commission.setStatus("waived");
        commissionRepository.saveAndFlush(commission);

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.paymentId=='" + estimated.getId() + "')].commissionEstimated", hasItem(true)))
                .andExpect(jsonPath("$.content[?(@.paymentId=='" + estimated.getId() + "')].commissionAmount", hasItem(22500.0)))
                .andExpect(jsonPath("$.content[?(@.paymentId=='" + estimated.getId() + "')].netAmount", hasItem(157500.0)))
                .andExpect(jsonPath("$.content[?(@.paymentId=='" + waived.getId() + "')].commissionAmount", hasItem(0)))
                .andExpect(jsonPath("$.content[?(@.paymentId=='" + waived.getId() + "')].netAmount", hasItem(180000.0)))
                .andExpect(jsonPath("$.totals.grossAmount").value(360000))
                .andExpect(jsonPath("$.totals.commissionAmount").value(22500))
                .andExpect(jsonPath("$.totals.netAmount").value(337500));
    }

    @Test
    @DisplayName("Tong cua tap loc khop tung dong; loc theo ngay thu, san; phan trang")
    void totalsFiltersPaging() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        Payment recent = collect();
        recordCommission(recent);
        Payment old = collect();
        recordCommission(old);
        old.setConfirmedAt(today.minusDays(40).atTime(10, 0).atZone(ZONE).toInstant());
        paymentRepository.saveAndFlush(old);
        Venue second = persistVenue(owner, "San Thu Hai");
        Payment other = collect(second.getCourts().getFirst());
        recordCommission(other);
        refund(other, "confirmed", "10000");

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totals.grossAmount").value(360000))
                .andExpect(jsonPath("$.totals.commissionAmount").value(45000))
                .andExpect(jsonPath("$.totals.refundedAmount").value(10000))
                .andExpect(jsonPath("$.totals.netAmount").value(305000));
        mockMvc.perform(get(PATH).param("from", today.minusDays(60).toString()).param("to", today.toString())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totals.bookings").value(3));
        mockMvc.perform(get(PATH).param("venueId", venue.getId().toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totals.netAmount").value(157500));
        mockMvc.perform(get(PATH).param("from", today.minusDays(60).toString()).param("size", "2").param("page", "1")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totals.bookings").value(3));
    }

    @Test
    @DisplayName("Chi thay booking cua san minh; tham so sai 400; nguoi choi 403; chua dang nhap 401")
    void ownershipAndValidation() throws Exception {
        collect();
        String otherToken = loginAs(persistOwner("other"));
        mockMvc.perform(get(PATH).header("Authorization", bearer(otherToken)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totals.netAmount").value(0));
        mockMvc.perform(get(PATH).param("venueId", venue.getId().toString()).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PATH).param("from", "2026-12-10").param("to", "2026-12-01").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.to").exists());
        mockMvc.perform(get(PATH).param("from", "2024-01-01").param("to", "2026-01-01").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("size", "101").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ helpers

    private Payment collect() throws Exception {
        return collect(court);
    }

    /** Booking 90 phut (180.000d) da duoc chu san xac nhan thu tien. */
    private Payment collect(Court target) throws Exception {
        String bookingId = createBooking(playerToken, target, nextMonday(), LocalTime.of(6, 0).plusMinutes(100L * slot++));
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + bookingId + "/accept").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(UUID.fromString(bookingId)).orElseThrow();
        mockMvc.perform(post("/api/v1/payments/" + payment.getId() + "/report-paid").header("Authorization", bearer(playerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"cash\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/" + payment.getId() + "/confirm-received").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    private void recordCommission(Payment payment) {
        Booking booking = bookingRepository.findById(payment.getBooking().getId()).orElseThrow();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(Instant.now());
        bookingRepository.saveAndFlush(booking);
        commissionService.createForCompletedBooking(booking, Instant.now());
        commissionRepository.flush();
    }

    private void refund(Payment payment, String status, String amount) {
        jdbc.update("INSERT INTO refunds (id, payment_id, booking_id, amount, status) VALUES (gen_random_uuid(), ?, ?, ?::numeric, ?)",
                payment.getId(), payment.getBooking().getId(), amount, status);
    }
}
