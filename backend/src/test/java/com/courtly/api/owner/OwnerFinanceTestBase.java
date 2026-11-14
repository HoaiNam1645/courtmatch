package com.courtly.api.owner;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.FeeType;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.finance.CommissionSettlement;
import com.courtly.domain.finance.CommissionSettlementRepository;
import com.courtly.domain.finance.PlatformFeeConfig;
import com.courtly.domain.finance.PlatformFeeConfigRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import com.courtly.service.finance.CommissionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

/** Nen chung cho test tai chinh chu san (2.3.22+): chu san, san, chinh sach 10% va ham tao hoa hong / ky doi soat. */
abstract class OwnerFinanceTestBase extends OwnerApiTestBase {

    @Autowired protected BookingRepository bookingRepository;
    @Autowired protected BookingCommissionRepository commissionRepository;
    @Autowired protected CommissionSettlementRepository settlementRepository;
    @Autowired protected PlatformFeeConfigRepository feeConfigRepository;
    @Autowired protected CommissionService commissionService;

    protected User owner;
    protected String ownerToken;
    protected Venue venue;
    protected Court court;
    protected String playerToken;
    private int slot;

    @BeforeEach
    void setUpFinance() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-fin");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Tai Chinh");
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
        policy.setFeeValue(new BigDecimal("10.00"));
        policy.setEffectiveFrom(Instant.now().plusSeconds(5));
        policy.setStatus(ActiveStatus.ACTIVE);
        feeConfigRepository.saveAndFlush(policy);
        slot = 0;
    }

    /** Booking hoan thanh co hoa hong 10% = 18.000d, o trang thai va han thanh toan cho truoc. */
    protected BookingCommission commission(Court target, String status, Instant dueAt) throws Exception {
        String id = createBooking(playerToken, target, nextMonday(), LocalTime.of(6, 0).plusMinutes(100L * slot++));
        Booking booking = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(Instant.now());
        bookingRepository.saveAndFlush(booking);
        commissionService.createForCompletedBooking(booking, Instant.now());
        BookingCommission saved = commissionRepository.findByBookingId(booking.getId()).orElseThrow();
        saved.setStatus(status);
        saved.setDueAt(dueAt);
        return commissionRepository.saveAndFlush(saved);
    }

    protected CommissionSettlement settlement(User forOwner, LocalDate from, LocalDate to, String status,
                                              String commissionTotal, String amountDue, String amountPaid) {
        CommissionSettlement settlement = new CommissionSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setOwner(forOwner);
        settlement.setPeriodFrom(from);
        settlement.setPeriodTo(to);
        settlement.setCommissionTotal(new BigDecimal(commissionTotal));
        settlement.setAmountDue(new BigDecimal(amountDue));
        settlement.setAmountPaid(new BigDecimal(amountPaid));
        settlement.setStatus(status);
        return settlementRepository.saveAndFlush(settlement);
    }
}
