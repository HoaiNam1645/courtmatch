package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import com.courtly.service.finance.CommissionService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 2.3.18 - Xem phi nen tang cua booking. */
class OwnerCommissionTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/owner/commissions";

    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingCommissionRepository commissionRepository;
    @Autowired private PlatformFeeConfigRepository feeConfigRepository;
    @Autowired private CommissionService commissionService;

    private User owner;
    private String ownerToken;
    private Venue venue;
    private Court court;
    private String playerToken;
    private PlatformFeeConfig policy;
    private int slot;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-commission");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Hoa Hong");
        court = venue.getCourts().getFirst();
        playerToken = loginAs(persistUser("player", Role.PLAYER));
        // Moi luc chi co mot chinh sach active (rang buoc pfc_single_active): tat chinh sach hien co
        // roi dung 12,5% de ket qua xac dinh. Giao dich cua test duoc rollback.
        feeConfigRepository.findFirstByStatusOrderByEffectiveFromDesc(ActiveStatus.ACTIVE).ifPresent(existing -> {
            existing.setStatus(ActiveStatus.INACTIVE);
            feeConfigRepository.saveAndFlush(existing);
        });
        policy = new PlatformFeeConfig();
        policy.setId(UUID.randomUUID());
        policy.setName("Hoa hong thu nghiem");
        policy.setFeeType(FeeType.PERCENTAGE);
        policy.setFeeValue(new java.math.BigDecimal("12.50"));
        policy.setEffectiveFrom(Instant.now().plusSeconds(5));
        policy.setStatus(ActiveStatus.ACTIVE);
        feeConfigRepository.saveAndFlush(policy);
        slot = 0;
    }

    @Test
    @DisplayName("Moi booking hoan thanh co phi chup theo chinh sach luc ghi nhan; doi chinh sach sau do khong doi phi cu")
    void snapshotKeepsOriginalPolicy() throws Exception {
        String id = complete();
        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].bookingId").value(id))
                .andExpect(jsonPath("$.content[0].grossAmount").value(180000))
                .andExpect(jsonPath("$.content[0].feeType").value("percentage"))
                .andExpect(jsonPath("$.content[0].feeValue").value(12.5))
                .andExpect(jsonPath("$.content[0].commissionAmount").value(22500))
                .andExpect(jsonPath("$.content[0].policyName").value("Hoa hong thu nghiem"))
                .andExpect(jsonPath("$.content[0].status").value("due"))
                .andExpect(jsonPath("$.content[0].overdue").value(false))
                .andExpect(jsonPath("$.content[0].dueAt").exists())
                .andExpect(jsonPath("$.totalCommission").value(22500))
                .andExpect(jsonPath("$.activePolicy.feeValue").value(12.5));

        // Quan tri vien doi sang chinh sach 20%: phi cu giu nguyen, phi moi theo 20%.
        policy.setStatus(ActiveStatus.INACTIVE);
        feeConfigRepository.saveAndFlush(policy);
        PlatformFeeConfig newer = new PlatformFeeConfig();
        newer.setId(UUID.randomUUID());
        newer.setName("Hoa hong moi");
        newer.setFeeType(FeeType.FIXED);
        newer.setFeeValue(new java.math.BigDecimal("30000"));
        newer.setEffectiveFrom(Instant.now().plusSeconds(10));
        newer.setStatus(ActiveStatus.ACTIVE);
        feeConfigRepository.saveAndFlush(newer);
        String second = complete();

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[?(@.bookingId=='" + id + "')].commissionAmount", hasItem(22500.0)))
                .andExpect(jsonPath("$.content[?(@.bookingId=='" + second + "')].commissionAmount", hasItem(30000.0)))
                .andExpect(jsonPath("$.content[?(@.bookingId=='" + second + "')].feeType", hasItem("fixed")))
                .andExpect(jsonPath("$.totalCommission").value(52500))
                .andExpect(jsonPath("$.activePolicy.name").value("Hoa hong moi"));
    }

    @Test
    @DisplayName("Chi tiet booking: da ghi nhan -> snapshot; da xac nhan chua choi xong -> uoc tinh; con lai khong co phi")
    void bookingDetailShowsFee() throws Exception {
        String completed = complete();
        mockMvc.perform(get("/api/v1/owner/bookings/" + completed).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.fee.estimated").value(false))
                .andExpect(jsonPath("$.fee.commissionAmount").value(22500))
                .andExpect(jsonPath("$.fee.policyName").value("Hoa hong thu nghiem"))
                .andExpect(jsonPath("$.fee.status").value("due"));

        String confirmed = createBooking(playerToken, court, nextMonday(), at());
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + confirmed + "/accept").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        Booking booking = bookingRepository.findById(UUID.fromString(confirmed)).orElseThrow();
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.saveAndFlush(booking);
        mockMvc.perform(get("/api/v1/owner/bookings/" + confirmed).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.fee.estimated").value(true))
                .andExpect(jsonPath("$.fee.commissionAmount").value(22500))
                .andExpect(jsonPath("$.fee.status").doesNotExist());
        assertThat(commissionRepository.findByBookingId(UUID.fromString(confirmed))).isEmpty();

        String pending = createBooking(playerToken, court, nextMonday(), at());
        mockMvc.perform(get("/api/v1/owner/bookings/" + pending).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.fee").doesNotExist());
    }

    @Test
    @DisplayName("Loc theo trang thai, san, ngay ghi nhan; qua han duoc danh dau; phan trang")
    void filtersAndOverdue() throws Exception {
        String old = complete();
        String recent = complete();
        BookingCommission overdue = commissionRepository.findByBookingId(UUID.fromString(old)).orElseThrow();
        overdue.setDueAt(Instant.now().minus(2, ChronoUnit.DAYS));
        overdue.setCalculatedAt(Instant.now().minus(20, ChronoUnit.DAYS));
        commissionRepository.saveAndFlush(overdue);
        BookingCommission suspended = commissionRepository.findByBookingId(UUID.fromString(recent)).orElseThrow();
        suspended.setStatus("suspended");
        commissionRepository.saveAndFlush(suspended);

        mockMvc.perform(get(PATH).param("status", "due").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].bookingId").value(old))
                .andExpect(jsonPath("$.content[0].overdue").value(true));
        mockMvc.perform(get(PATH).param("status", "suspended").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content[0].bookingId").value(recent))
                .andExpect(jsonPath("$.content[0].overdue").value(false))
                .andExpect(jsonPath("$.totalCommission").value(22500));
        mockMvc.perform(get(PATH).param("status", "settled").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalCommission").value(0));

        LocalDate today = LocalDate.now(ZONE);
        mockMvc.perform(get(PATH).param("from", today.minusDays(1).toString()).param("to", today.toString())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].bookingId").value(recent));
        mockMvc.perform(get(PATH).param("venueId", venue.getId().toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(PATH).param("size", "1").param("page", "1").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("Chi thay hoa hong cua san minh; tham so sai 400; nguoi choi 403; chua dang nhap 401")
    void ownershipAndValidation() throws Exception {
        complete();
        String otherToken = loginAs(persistOwner("other"));
        mockMvc.perform(get(PATH).header("Authorization", bearer(otherToken)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalCommission").value(0));
        mockMvc.perform(get(PATH).param("venueId", venue.getId().toString()).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PATH).param("status", "bay").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
        mockMvc.perform(get(PATH).param("from", "2026-12-10").param("to", "2026-12-01").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.to").exists());
        mockMvc.perform(get(PATH).param("size", "101").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    /** Booking da choi xong va da ghi nhan hoa hong theo chinh sach dang hieu luc. */
    private String complete() throws Exception {
        String id = createBooking(playerToken, court, nextMonday(), at());
        Booking booking = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(Instant.now());
        bookingRepository.saveAndFlush(booking);
        commissionService.createForCompletedBooking(booking, Instant.now());
        commissionRepository.flush();
        return id;
    }

    private LocalTime at() {
        return LocalTime.of(6, 0).plusMinutes(100L * slot++);
    }
}
