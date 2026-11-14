package com.courtly.api.owner;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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

/** 2.3.21 - Xem chi tiet cac booking cau thanh cong no hoa hong. */
class OwnerDebtBookingsTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/owner/commission-debt/bookings";

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
        owner = persistOwner("owner-debtbk");
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
    @DisplayName("Liet ke booking cau thanh cong no, han som nhat truoc; bo suspended/settled/waived")
    void listsOnlyDebtBookings() throws Exception {
        Instant now = Instant.now();
        commission(court, "due", now.plus(5, ChronoUnit.DAYS));
        commission(court, "due", now.minus(3, ChronoUnit.DAYS));
        commission(court, "adjusted", now.minus(1, ChronoUnit.DAYS));
        commission(court, "pending", now.plus(2, ChronoUnit.DAYS));
        commission(court, "suspended", now.minus(5, ChronoUnit.DAYS));
        commission(court, "settled", now.minus(9, ChronoUnit.DAYS));
        commission(court, "waived", now.minus(9, ChronoUnit.DAYS));

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalAmount").value(72000))
                .andExpect(jsonPath("$.content", hasSize(4)))
                .andExpect(jsonPath("$.content[0].overdue").value(true))
                .andExpect(jsonPath("$.content[1].overdue").value(true))
                .andExpect(jsonPath("$.content[2].overdue").value(false))
                .andExpect(jsonPath("$.content[0].status").value("due"))
                .andExpect(jsonPath("$.content[0].bookingCode").exists())
                .andExpect(jsonPath("$.content[0].commissionAmount").value(18000))
                .andExpect(jsonPath("$.content[*].status", not(hasItem("suspended"))))
                .andExpect(jsonPath("$.content[*].status", not(hasItem("settled"))));
    }

    @Test
    @DisplayName("Loc theo nhom qua han / chua den han va theo san; phan trang")
    void filtersAndPaging() throws Exception {
        Venue second = persistVenue(owner, "San Thu Hai");
        Instant now = Instant.now();
        commission(court, "due", now.minus(2, ChronoUnit.DAYS));
        commission(court, "due", now.plus(2, ChronoUnit.DAYS));
        commission(second.getCourts().getFirst(), "due", now.minus(4, ChronoUnit.DAYS));
        commission(second.getCourts().getFirst(), "pending", now.plus(4, ChronoUnit.DAYS));

        mockMvc.perform(get(PATH).param("bucket", "overdue").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalAmount").value(36000))
                .andExpect(jsonPath("$.content[*].overdue", everyItem(is(true))));
        mockMvc.perform(get(PATH).param("bucket", "upcoming").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].overdue", everyItem(is(false))));
        mockMvc.perform(get(PATH).param("venueId", second.getId().toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].venueName", everyItem(is("San Thu Hai"))));
        mockMvc.perform(get(PATH).param("size", "3").param("page", "1").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalAmount").value(72000));
    }

    @Test
    @DisplayName("Chua co cong no: danh sach rong, tong 0")
    void empty() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalAmount").value(0));
    }

    @Test
    @DisplayName("Tham so sai 400; san cua chu khac 404; chu san khac thay rong; nguoi choi 403; chua dang nhap 401")
    void validationAndOwnership() throws Exception {
        commission(court, "due", Instant.now().plus(3, ChronoUnit.DAYS));
        mockMvc.perform(get(PATH).param("bucket", "xyz").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.bucket").exists());
        mockMvc.perform(get(PATH).param("size", "500").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("page", "-1").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        User other = persistOwner("other");
        String otherToken = loginAs(other);
        mockMvc.perform(get(PATH).header("Authorization", bearer(otherToken)))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get(PATH).param("venueId", venue.getId().toString()).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PATH).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    /** Booking hoan thanh co hoa hong 10% = 18.000d o trang thai va han thanh toan cho truoc. */
    private void commission(Court target, String status, Instant dueAt) throws Exception {
        String id = createBooking(playerToken, target, nextMonday(), LocalTime.of(6, 0).plusMinutes(100L * slot++));
        Booking booking = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(Instant.now());
        bookingRepository.saveAndFlush(booking);
        commissionService.createForCompletedBooking(booking, Instant.now());
        BookingCommission saved = commissionRepository.findByBookingId(booking.getId()).orElseThrow();
        saved.setStatus(status);
        saved.setDueAt(dueAt);
        commissionRepository.saveAndFlush(saved);
    }
}
