package com.courtly.api.admin;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.FeeType;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.finance.CommissionSettlement;
import com.courtly.domain.finance.CommissionSettlementRepository;
import com.courtly.domain.venue.Venue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 2.3.44 - Xem chi tiet doi soat booking. */
class AdminCommissionDetailTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/finance/commission-transactions/";

    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingCommissionRepository commissionRepository;
    @Autowired private CommissionSettlementRepository settlementRepository;
    @Autowired private JdbcTemplate jdbc;

    private String adminToken;
    private User owner;
    private User playerUser;
    private Venue venue;
    private String player;
    private int index;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        adminToken = loginAs(persistAdmin("admin-detail"));
        owner = persistOwner("owner-detail");
        venue = persistVenue(owner, "San Chi Tiet");
        playerUser = persistUser("player-detail", Role.PLAYER);
        player = loginAs(playerUser);
    }

    private Booking newBooking(String status) throws Exception {
        String id = createBooking(player, venue.getCourts().getFirst(), nextMonday().plusDays(index++), LocalTime.of(9, 0));
        Booking booking = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
        booking.setStatus(com.courtly.common.enums.BookingStatus.valueOf(status));
        if ("COMPLETED".equals(status)) {
            booking.setCompletedAt(Instant.now());
        }
        return bookingRepository.saveAndFlush(booking);
    }

    private BookingCommission commission(Booking booking, String status, String commissionAmount, CommissionSettlement settlement) {
        BookingCommission commission = new BookingCommission();
        commission.setId(UUID.randomUUID());
        commission.setBooking(booking);
        commission.setOwner(owner);
        commission.setGrossAmount(booking.getTotalAmount());
        commission.setFeeType(FeeType.PERCENTAGE);
        commission.setFeeValue(BigDecimal.TEN);
        commission.setCommissionAmount(new BigDecimal(commissionAmount));
        commission.setStatus(status);
        Instant now = Instant.now();
        commission.setCalculatedAt(now);
        commission.setDueAt(now.plus(7, ChronoUnit.DAYS));
        commission.setSettlement(settlement);
        return commissionRepository.saveAndFlush(commission);
    }

    private void payment(Booking booking, String status) {
        jdbc.update("""
                INSERT INTO payments (id, booking_id, amount, status, recipient_owner_id, method, payer_reported_at, confirmed_at)
                VALUES (gen_random_uuid(), ?, ?, ?, ?, 'cash', now(), CASE WHEN ? = 'confirmed' THEN now() ELSE NULL END)
                """, booking.getId(), booking.getTotalAmount(), status, owner.getId(), status);
    }

    @Test
    @DisplayName("Chi tiet day du: hoa hong da chot, booking, chu san, thanh toan, ky doi soat")
    void fullDetail() throws Exception {
        Booking booking = newBooking("COMPLETED");
        payment(booking, "confirmed");
        CommissionSettlement settlement = new CommissionSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setOwner(owner);
        settlement.setPeriodFrom(LocalDate.parse("2026-11-01"));
        settlement.setPeriodTo(LocalDate.parse("2026-11-07"));
        settlement.setStatus("partial");
        settlementRepository.saveAndFlush(settlement);
        BookingCommission commission = commission(booking, "due", "18000", settlement);

        mockMvc.perform(get(PATH + commission.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commission.id").value(commission.getId().toString()))
                .andExpect(jsonPath("$.commission.status").value("due"))
                .andExpect(jsonPath("$.commission.feeType").value("percentage"))
                .andExpect(jsonPath("$.commission.feeValue").value(10))
                .andExpect(jsonPath("$.commission.commissionAmount").value(18000))
                .andExpect(jsonPath("$.commission.baseCommission").value(18000))
                .andExpect(jsonPath("$.commission.voucherDiscount").value(0))
                .andExpect(jsonPath("$.commission.courtlyOwed").value(0))
                .andExpect(jsonPath("$.commission.overdue").value(false))
                .andExpect(jsonPath("$.booking.bookingCode").value(booking.getBookingCode()))
                .andExpect(jsonPath("$.booking.venueName").value("San Chi Tiet"))
                .andExpect(jsonPath("$.booking.totalAmount").value(180000))
                .andExpect(jsonPath("$.booking.payableAmount").value(180000))
                .andExpect(jsonPath("$.booking.voucherCode").doesNotExist())
                .andExpect(jsonPath("$.booking.startTime").exists())
                .andExpect(jsonPath("$.owner.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.payment.status").value("confirmed"))
                .andExpect(jsonPath("$.unconfirmedPayment").value(false))
                .andExpect(jsonPath("$.settlement.status").value("partial"))
                .andExpect(jsonPath("$.refunds", hasSize(0)))
                .andExpect(jsonPath("$.disputes", hasSize(0)))
                .andExpect(jsonPath("$.history", hasSize(0)));
    }

    @Test
    @DisplayName("Booking co voucher: gia goc, tien giam, so phai tra, hoa hong day du va khoan Courtly no")
    void voucherBreakdown() throws Exception {
        Booking booking = newBooking("COMPLETED");
        jdbc.update("INSERT INTO vouchers (id, code, name, discount_percent, valid_from, valid_until, status) "
                + "VALUES (gen_random_uuid(), 'GIAM40', 'Giam 40', 40, now() - interval '1 day', now() + interval '30 days', 'active')");
        UUID voucherId = jdbc.queryForObject("SELECT id FROM vouchers WHERE code = 'GIAM40'", UUID.class);
        jdbc.update("UPDATE bookings SET voucher_id = ?, discount_amount = 72000 WHERE id = ?", voucherId, booking.getId());
        BookingCommission commission = commission(booking, "due", "0", null);
        jdbc.update("UPDATE booking_commissions SET voucher_discount = 72000, courtly_owed = 54000 WHERE id = ?", commission.getId());

        mockMvc.perform(get(PATH + commission.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.voucherCode").value("GIAM40"))
                .andExpect(jsonPath("$.booking.totalAmount").value(180000))
                .andExpect(jsonPath("$.booking.discountAmount").value(72000))
                .andExpect(jsonPath("$.booking.payableAmount").value(108000))
                .andExpect(jsonPath("$.commission.baseCommission").value(18000))
                .andExpect(jsonPath("$.commission.voucherDiscount").value(72000))
                .andExpect(jsonPath("$.commission.commissionAmount").value(0))
                .andExpect(jsonPath("$.commission.courtlyOwed").value(54000))
                .andExpect(jsonPath("$.settlement").doesNotExist());
    }

    @Test
    @DisplayName("Booking tu hoan thanh khi chu san chua xac nhan thu tien duoc danh dau; thanh toan da xac nhan thi khong")
    void unconfirmedFlag() throws Exception {
        Booking auto = newBooking("COMPLETED");
        payment(auto, "payer_reported");
        Booking confirmed = newBooking("COMPLETED");
        payment(confirmed, "confirmed");
        Booking refunded = newBooking("COMPLETED");
        payment(refunded, "partially_refunded");
        Booking noPayment = newBooking("COMPLETED");

        for (Object[] row : new Object[][] {{auto, true}, {confirmed, false}, {refunded, false}, {noPayment, false}}) {
            BookingCommission commission = commission((Booking) row[0], "due", "18000", null);
            mockMvc.perform(get(PATH + commission.getId()).header("Authorization", bearer(adminToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.unconfirmedPayment").value(row[1]));
        }
    }

    @Test
    @DisplayName("Hien thi hoan tien, tranh chap va lich su dieu chinh tu audit_logs, moi nhat truoc")
    void refundsDisputesAndHistory() throws Exception {
        Booking booking = newBooking("COMPLETED");
        payment(booking, "partially_refunded");
        UUID paymentId = jdbc.queryForObject("SELECT id FROM payments WHERE booking_id = ?", UUID.class, booking.getId());
        jdbc.update("INSERT INTO refunds (id, payment_id, booking_id, amount, status, reason) VALUES (gen_random_uuid(), ?, ?, 50000, 'confirmed', 'San ngung hoat dong')",
                paymentId, booking.getId());
        jdbc.update("INSERT INTO booking_disputes (id, booking_id, payment_id, reporter_id, category, description, status) "
                + "VALUES (gen_random_uuid(), ?, ?, ?, 'venue_closed', 'Chua nhan san', 'reviewing')", booking.getId(), paymentId, playerUser.getId());
        BookingCommission commission = commission(booking, "adjusted", "12000", null);
        jdbc.update("INSERT INTO audit_logs (id, actor_id, action, entity_type, entity_id, old_data, new_data, created_at) "
                + "VALUES (gen_random_uuid(), ?, 'commission.adjust', 'booking_commission', ?, '{\"commissionAmount\":18000}'::jsonb, "
                + "'{\"commissionAmount\":12000}'::jsonb, now() - interval '1 hour')", owner.getId(), commission.getId());
        jdbc.update("INSERT INTO audit_logs (id, actor_id, action, entity_type, entity_id, old_data, new_data, created_at) "
                + "VALUES (gen_random_uuid(), ?, 'commission.adjust', 'booking_commission', ?, '{\"commissionAmount\":12000}'::jsonb, "
                + "'{\"commissionAmount\":10000}'::jsonb, now())", owner.getId(), commission.getId());

        mockMvc.perform(get(PATH + commission.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunds", hasSize(1)))
                .andExpect(jsonPath("$.refunds[0].amount").value(50000))
                .andExpect(jsonPath("$.refunds[0].status").value("confirmed"))
                .andExpect(jsonPath("$.disputes", hasSize(1)))
                .andExpect(jsonPath("$.disputes[0].status").value("reviewing"))
                .andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.history[0].newData.commissionAmount").value(10000))
                .andExpect(jsonPath("$.history[1].newData.commissionAmount").value(12000))
                .andExpect(jsonPath("$.history[0].actorName").exists());
    }

    @Test
    @DisplayName("Id khong ton tai 404, id sai dinh dang 400")
    void notFoundAndInvalid() throws Exception {
        mockMvc.perform(get(PATH + UUID.randomUUID()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "khong-phai-uuid").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Can quyen finance.commission.view; chu san va nguoi choi 403; chua dang nhap 401; chi doc (POST 405)")
    void access() throws Exception {
        Booking booking = newBooking("COMPLETED");
        BookingCommission commission = commission(booking, "due", "18000", null);
        String path = PATH + commission.getId();
        mockMvc.perform(get(path).header("Authorization", bearer(loginAs(owner)))).andExpect(status().isForbidden());
        mockMvc.perform(get(path).header("Authorization", bearer(player))).andExpect(status().isForbidden());
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(path).header("Authorization", bearer(adminToken))).andExpect(status().isMethodNotAllowed());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "finance.commission.view".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(path).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }
}
