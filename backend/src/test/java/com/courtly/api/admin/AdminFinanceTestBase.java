package com.courtly.api.admin;

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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Nen chung cho test cong no hoa hong phia quan tri (2.3.47 - 2.3.52): chu san, san, nguoi choi va ham dung
 * hoa hong, ky doi soat, bao cao thanh toan truc tiep trong database.
 */
abstract class AdminFinanceTestBase extends AdminApiTestBase {

    protected static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired protected BookingRepository bookingRepository;
    @Autowired protected BookingCommissionRepository commissionRepository;
    @Autowired protected CommissionSettlementRepository settlementRepository;
    @Autowired protected JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext protected jakarta.persistence.EntityManager entityManager;

    protected String adminToken;
    protected User admin;
    protected User owner;
    protected User otherOwner;
    protected Venue venue;
    protected Venue otherVenue;
    protected String player;
    protected int index;
    protected int refCounter;
    protected int settlementCounter;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-confirm");
        adminToken = loginAs(admin);
        owner = persistOwner("owner-confirm");
        otherOwner = persistOwner("owner-confirm-2");
        venue = persistVenue(owner, "San Xac Nhan");
        otherVenue = persistVenue(otherOwner, "San Xac Nhan 2");
        player = loginAs(persistUser("player-confirm", Role.PLAYER));
    }

    protected BookingCommission commission(User commissionOwner, Venue target, String status, String amount, int dueInDays,
                                         CommissionSettlement settlement) throws Exception {
        String id = createBooking(player, target.getCourts().getFirst(), nextMonday().plusDays(index++), LocalTime.of(9, 0));
        Booking booking = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
        BookingCommission commission = new BookingCommission();
        commission.setId(UUID.randomUUID());
        commission.setBooking(booking);
        commission.setOwner(commissionOwner);
        commission.setGrossAmount(new BigDecimal("180000"));
        commission.setFeeType(FeeType.PERCENTAGE);
        commission.setFeeValue(BigDecimal.TEN);
        commission.setCommissionAmount(new BigDecimal(amount));
        commission.setStatus(status);
        Instant calculated = Instant.now().minus(10, ChronoUnit.DAYS).plusSeconds(index);
        commission.setCalculatedAt(calculated);
        commission.setDueAt(Instant.now().plus(dueInDays, ChronoUnit.DAYS));
        commission.setCreatedAt(calculated);
        commission.setSettlement(settlement);
        return commissionRepository.saveAndFlush(commission);
    }

    protected CommissionSettlement settlement(User settlementOwner, String due, String paid, String status) {
        CommissionSettlement settlement = new CommissionSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setOwner(settlementOwner);
        LocalDate start = LocalDate.now(VN).minusDays(60L + 10L * settlementCounter++);
        settlement.setPeriodFrom(start);
        settlement.setPeriodTo(start.plusDays(6));
        settlement.setStatus(status);
        settlement.setCommissionTotal(new BigDecimal(due));
        settlement.setAmountDue(new BigDecimal(due));
        settlement.setAmountPaid(new BigDecimal(paid));
        return settlementRepository.saveAndFlush(settlement);
    }

    protected UUID report(User reportOwner, CommissionSettlement settlement, String amount) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO commission_payment_reports (id, owner_id, settlement_id, amount, paid_on, reference_code, status) "
                + "VALUES (?, ?, ?, ?::numeric, current_date, ?, 'pending_review')", id, reportOwner.getId(),
                settlement == null ? null : settlement.getId(), amount, "REF-" + (++refCounter) + "-" + id.toString().substring(0, 6));
        return id;
    }

    protected String statusOf(BookingCommission commission) {
        return jdbc.queryForObject("SELECT status FROM booking_commissions WHERE id = ?", String.class, commission.getId());
    }

    protected Map<String, Object> settlementRow(CommissionSettlement settlement) {
        return jdbc.queryForMap("SELECT status, amount_due, amount_paid, adjustment_total, paid_at, reviewed_by FROM commission_settlements WHERE id = ?", settlement.getId());
    }

    protected BigDecimal credit(User target) {
        return jdbc.queryForObject("SELECT commission_credit FROM court_owner_profiles WHERE user_id = ?", BigDecimal.class, target.getId());
    }

    protected Object clearedAt(BookingCommission commission) {
        return jdbc.queryForObject("SELECT courtly_owed_cleared_at FROM booking_commissions WHERE id = ?", Object.class, commission.getId());
    }

    /** Khoan thanh toan truc tiep cua booking cua hoa hong; amount mac dinh la gia booking. */
    protected UUID payment(BookingCommission commission, String paymentStatus, String amount) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payments (id, booking_id, amount, status, recipient_owner_id, method, payer_reported_at, confirmed_at)
                VALUES (?, ?, COALESCE(?::numeric, (SELECT total_amount FROM bookings WHERE id = ?)), ?, ?, 'cash', now(),
                        CASE WHEN ? IN ('confirmed', 'partially_refunded', 'refunded') THEN now() ELSE NULL END)
                """, id, commission.getBooking().getId(), amount, commission.getBooking().getId(), paymentStatus, commission.getOwner().getId(),
                paymentStatus);
        return id;
    }
}
