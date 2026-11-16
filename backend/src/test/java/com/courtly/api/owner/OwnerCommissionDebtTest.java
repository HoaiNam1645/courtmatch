package com.courtly.api.owner;

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
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import com.courtly.service.finance.CommissionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 2.3.20 - Xem tong cong no hoa hong voi Courtly. */
class OwnerCommissionDebtTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/owner/commission-debt";

    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingCommissionRepository commissionRepository;
    @Autowired private PlatformFeeConfigRepository feeConfigRepository;
    @Autowired private CommissionService commissionService;

    private User owner;
    private String ownerToken;
    private Venue venue;
    private Court court;
    private String playerToken;
    private int slot;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-debt");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Cong No");
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

    @Test
    @DisplayName("Chua co hoa hong thi cong no bang 0, khong co han")
    void noCommissions() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").value(0))
                .andExpect(jsonPath("$.outstandingCount").value(0))
                .andExpect(jsonPath("$.overdueAmount").value(0))
                .andExpect(jsonPath("$.settledAmount").value(0))
                .andExpect(jsonPath("$.oldestOverdueAt").doesNotExist())
                .andExpect(jsonPath("$.nextDueAt").doesNotExist())
                .andExpect(jsonPath("$.byVenue", hasSize(0)));
    }

    @Test
    @DisplayName("Cong no = pending + due + adjusted, tach qua han / chua den han; suspended, settled, waived khong tinh vao cong no")
    void aggregatesFromBookingCommissions() throws Exception {
        Instant now = Instant.now();
        commission(court, "due", now.minus(3, ChronoUnit.DAYS));          // qua han 18.000
        commission(court, "adjusted", now.minus(1, ChronoUnit.DAYS));     // qua han 18.000
        commission(court, "pending", now.plus(2, ChronoUnit.DAYS));       // chua den han 18.000
        commission(court, "due", now.plus(5, ChronoUnit.DAYS));           // chua den han 18.000
        commission(court, "suspended", now.minus(5, ChronoUnit.DAYS));    // tam hoan 18.000, khong tinh
        commission(court, "settled", now.minus(9, ChronoUnit.DAYS));      // da doi soat 18.000
        commission(court, "waived", now.minus(9, ChronoUnit.DAYS));       // mien

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").value(72000))
                .andExpect(jsonPath("$.outstandingCount").value(4))
                .andExpect(jsonPath("$.overdueAmount").value(36000))
                .andExpect(jsonPath("$.overdueCount").value(2))
                .andExpect(jsonPath("$.upcomingAmount").value(36000))
                .andExpect(jsonPath("$.upcomingCount").value(2))
                .andExpect(jsonPath("$.suspendedAmount").value(18000))
                .andExpect(jsonPath("$.suspendedCount").value(1))
                .andExpect(jsonPath("$.settledAmount").value(18000))
                .andExpect(jsonPath("$.oldestOverdueAt").exists())
                .andExpect(jsonPath("$.nextDueAt").exists())
                .andExpect(jsonPath("$.byVenue", hasSize(1)))
                .andExpect(jsonPath("$.byVenue[0].venueName").value("San Cong No"))
                .andExpect(jsonPath("$.byVenue[0].outstanding").value(72000))
                .andExpect(jsonPath("$.byVenue[0].overdue").value(36000))
                .andExpect(jsonPath("$.byVenue[0].bookings").value(4));
    }

    @Test
    @DisplayName("Chia theo san, sap giam dan theo cong no; han cu nhat va han gan nhat dung")
    void perVenueAndDeadlines() throws Exception {
        Venue second = persistVenue(owner, "San Thu Hai");
        Instant oldest = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant soon = Instant.now().plus(1, ChronoUnit.DAYS);
        commission(court, "due", Instant.now().minus(2, ChronoUnit.DAYS));
        commission(second.getCourts().getFirst(), "due", oldest);
        commission(second.getCourts().getFirst(), "due", Instant.now().plus(6, ChronoUnit.DAYS));
        commission(second.getCourts().getFirst(), "adjusted", Instant.now().plus(4, ChronoUnit.DAYS));
        commission(court, "pending", soon);

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalOutstanding").value(90000))
                .andExpect(jsonPath("$.byVenue", hasSize(2)))
                .andExpect(jsonPath("$.byVenue[0].venueName").value("San Thu Hai"))
                .andExpect(jsonPath("$.byVenue[0].outstanding").value(54000))
                .andExpect(jsonPath("$.byVenue[0].overdue").value(18000))
                .andExpect(jsonPath("$.byVenue[0].bookings").value(3))
                .andExpect(jsonPath("$.byVenue[1].venueName").value("San Cong No"))
                .andExpect(jsonPath("$.oldestOverdueAt").value(org.hamcrest.Matchers.startsWith(
                        oldest.truncatedTo(ChronoUnit.MINUTES).toString().substring(0, 13))))
                .andExpect(jsonPath("$.nextDueAt").value(org.hamcrest.Matchers.startsWith(
                        soon.truncatedTo(ChronoUnit.MINUTES).toString().substring(0, 13))));
    }

    @Test
    @DisplayName("Chi tinh hoa hong cua san minh; chu san khac thay 0; nguoi choi 403; chua dang nhap 401")
    void ownership() throws Exception {
        commission(court, "due", Instant.now().plus(3, ChronoUnit.DAYS));
        String otherToken = loginAs(persistOwner("other"));
        mockMvc.perform(get(PATH).header("Authorization", bearer(otherToken)))
                .andExpect(jsonPath("$.totalOutstanding").value(0))
                .andExpect(jsonPath("$.byVenue", hasSize(0)));
        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalOutstanding").value(18000));
        mockMvc.perform(get(PATH).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH).header("Authorization", bearer(ownerToken))).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Khoan Courtly no chu san (voucher vuot hoa hong): cong don dung, khong tinh vao cong no, bo qua khoan da bu tru / mien / tam hoan")
    void courtlyOwed() throws Exception {
        Instant due = Instant.now().plus(3, ChronoUnit.DAYS);
        BookingCommission a = commission(court, "due", due);
        owe(a, "40000", null);
        BookingCommission b = commission(court, "settled", due);
        owe(b, "5000", null);
        BookingCommission cleared = commission(court, "due", due);
        owe(cleared, "9000", Instant.now());
        BookingCommission waived = commission(court, "waived", due);
        owe(waived, "7000", null);
        BookingCommission suspended = commission(court, "suspended", due);
        owe(suspended, "3000", null);
        commission(court, "due", due); // khong voucher

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courtlyOwed.amount").value(45000))
                .andExpect(jsonPath("$.courtlyOwed.count").value(2))
                .andExpect(jsonPath("$.courtlyOwed.items", hasSize(2)))
                .andExpect(jsonPath("$.courtlyOwed.items[?(@.owedAmount==40000)].venueName").value("San Cong No"))
                .andExpect(jsonPath("$.courtlyOwed.items[?(@.owedAmount==40000)].voucherDiscount").value(58000))
                .andExpect(jsonPath("$.totalOutstanding").value(54000));
    }

    @Test
    @DisplayName("Khong co khoan Courtly no thi tra ve 0 va danh sach rong; chu san khac khong thay khoan cua nguoi khac")
    void courtlyOwedEmptyAndIsolated() throws Exception {
        owe(commission(court, "due", Instant.now().plus(1, ChronoUnit.DAYS)), "12000", null);
        User other = persistOwner("owner-other");
        String otherToken = loginAs(other);

        mockMvc.perform(get(PATH).header("Authorization", bearer(otherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courtlyOwed.amount").value(0))
                .andExpect(jsonPath("$.courtlyOwed.count").value(0))
                .andExpect(jsonPath("$.courtlyOwed.items", hasSize(0)));
        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.courtlyOwed.amount").value(12000));
    }

    private void owe(BookingCommission commission, String owed, Instant clearedAt) {
        commission.setVoucherDiscount(new BigDecimal("58000"));
        commission.setCourtlyOwed(new BigDecimal(owed));
        commission.setCourtlyOwedClearedAt(clearedAt);
        commissionRepository.saveAndFlush(commission);
    }

    /** Booking hoan thanh co hoa hong 10% = 18.000d o trang thai va han thanh toan cho truoc. */
    private BookingCommission commission(Court target, String status, Instant dueAt) throws Exception {
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
}
