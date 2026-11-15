package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.FeeType;
import com.courtly.domain.account.CourtOwnerProfileRepository;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.finance.DebtRestrictionLevel;
import com.courtly.domain.finance.DebtRestrictionLevelRepository;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import com.courtly.service.owner.OwnerRestrictionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** B2 - han che chu san co cong no hoa hong qua han. */
class OwnerDebtRestrictionTest extends AdminApiTestBase {

    @Autowired private OwnerRestrictionService restrictionService;
    @Autowired private DebtRestrictionLevelRepository levelRepository;
    @Autowired private CourtOwnerProfileRepository profileRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingCommissionRepository commissionRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private User admin;
    private String adminToken;
    private User owner;
    private String ownerToken;
    private Venue venue;
    private Court court;
    private String playerToken;
    private int bookingIndex;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-restriction");
        adminToken = loginAs(admin);
        owner = persistOwner("owner-restriction");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Han Che");
        court = venue.getCourts().getFirst();
        playerToken = loginAs(persistUser("player-restriction", Role.PLAYER));
        // Dat nguong ro rang va tat, tung test tu bat.
        configure(false, "1000000", "14", false, "3000000", "30");
    }

    private void configure(boolean on1, String amount1, String days1, boolean on2, String amount2, String days2) {
        update(1, on1, amount1, days1);
        update(2, on2, amount2, days2);
        restrictionService.recomputeAll();
    }

    private void update(int level, boolean enabled, String amount, String days) {
        DebtRestrictionLevel item = levelRepository.findById((short) level).orElseThrow();
        item.setEnabled(enabled);
        item.setMinOverdueAmount(amount == null ? null : new BigDecimal(amount));
        item.setMinOverdueDays(days == null ? null : Integer.valueOf(days));
        levelRepository.saveAndFlush(item);
    }

    /** Hoa hong chua thu cua chu san, han thanh toan cach day {@code overdueDays} ngay (am = chua den han). */
    private BookingCommission commission(String status, String amount, int overdueDays) throws Exception {
        String id = createBooking(playerToken, court, nextMonday().plusDays(bookingIndex++), LocalTime.of(9, 0));
        Booking booking = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
        BookingCommission commission = new BookingCommission();
        commission.setId(UUID.randomUUID());
        commission.setBooking(booking);
        commission.setOwner(owner);
        commission.setGrossAmount(new BigDecimal(amount).multiply(BigDecimal.TEN));
        commission.setFeeType(FeeType.PERCENTAGE);
        commission.setFeeValue(BigDecimal.TEN);
        commission.setCommissionAmount(new BigDecimal(amount));
        commission.setStatus(status);
        Instant due = Instant.now().minus(overdueDays, ChronoUnit.DAYS).minusSeconds(60);
        commission.setCalculatedAt(due.minus(7, ChronoUnit.DAYS));
        commission.setDueAt(due);
        commission.setCreatedAt(due.minus(7, ChronoUnit.DAYS));
        return commissionRepository.saveAndFlush(commission);
    }

    private int stored() {
        return profileRepository.findById(owner.getId()).orElseThrow().getRestrictionLevel();
    }

    private org.springframework.test.web.servlet.ResultActions createBookingRequest() throws Exception {
        return mockMvc.perform(post("/api/v1/bookings").header("Authorization", bearer(playerToken)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"courtId":"%s","startTime":"%s","durationMinutes":90,"note":null}
                        """.formatted(court.getId(), nextMonday().plusDays(30 + bookingIndex++).atTime(LocalTime.of(10, 0)).atZone(ZONE).toInstant())));
    }

    // -------------------------------------------------------------- tinh muc han che

    @Test
    @DisplayName("Khong co cong no qua han: khong han che, du nguong da bat")
    void noOverdueMeansNoRestriction() throws Exception {
        configure(true, "1000", "1", true, "2000", "2");
        assertThat(restrictionService.evaluate(owner.getId(), Instant.now()).level()).isZero();
        commission("due", "5000000", -3);      // chua den han
        assertThat(restrictionService.evaluate(owner.getId(), Instant.now()).level()).isZero();
        assertThat(restrictionService.recompute(owner.getId())).isFalse();
        assertThat(stored()).isZero();
    }

    @Test
    @DisplayName("Muc theo so tien: du nguong muc 1 chan booking, du nguong muc 2 tam an san; muc cao nhat dat nguong duoc ap dung")
    void levelsByAmount() throws Exception {
        configure(true, "1000000", null, true, "3000000", null);
        commission("due", "900000", 1);
        assertThat(restrictionService.evaluate(owner.getId(), Instant.now()).level()).isZero();
        commission("pending", "100000", 1);
        OwnerRestrictionService.Evaluation first = restrictionService.evaluate(owner.getId(), Instant.now());
        assertThat(first.level()).isEqualTo(1);
        assertThat(first.byAmount()).isTrue();
        assertThat(first.byDays()).isFalse();
        commission("adjusted", "2000000", 1);
        assertThat(restrictionService.evaluate(owner.getId(), Instant.now()).level()).isEqualTo(2);
    }

    @Test
    @DisplayName("Muc theo so ngay: tinh tu han thanh toan cu nhat dang qua han, chi khi con cong no")
    void levelsByDays() throws Exception {
        configure(true, null, "7", true, null, "30");
        commission("due", "1000", 6);
        assertThat(restrictionService.evaluate(owner.getId(), Instant.now()).level()).isZero();
        commission("due", "1000", 8);
        OwnerRestrictionService.Evaluation level1 = restrictionService.evaluate(owner.getId(), Instant.now());
        assertThat(level1.level()).isEqualTo(1);
        assertThat(level1.byDays()).isTrue();
        assertThat(level1.byAmount()).isFalse();
        assertThat(level1.overdueDays()).isEqualTo(8);
        commission("pending", "1000", 31);
        assertThat(restrictionService.evaluate(owner.getId(), Instant.now()).level()).isEqualTo(2);
    }

    @Test
    @DisplayName("Dat mot trong hai nguong la ap dung: so ngay du du so tien thap, hoac nguoc lai")
    void eitherThresholdApplies() throws Exception {
        // Tao du hoa hong truoc khi bat han che, vi chu san bi han che thi khong tao them booking duoc.
        commission("due", "1000", 12);
        commission("due", "1000", 1);
        configure(true, "5000000", "10", false, null, null);
        assertThat(restrictionService.evaluate(owner.getId(), Instant.now()).level()).isEqualTo(1);   // chi du so ngay
        configure(true, "500", "100", false, null, null);
        assertThat(restrictionService.evaluate(owner.getId(), Instant.now()).level()).isEqualTo(1);   // chi du so tien
        configure(true, "500", "10", false, null, null);
        OwnerRestrictionService.Evaluation both = restrictionService.evaluate(owner.getId(), Instant.now());
        assertThat(both.byAmount()).isTrue();
        assertThat(both.byDays()).isTrue();
    }

    @Test
    @DisplayName("Chi hoa hong chua thu (pending, due, adjusted) moi tinh; tam hoan, duoc mien, da doi soat khong tinh")
    void onlyUnpaidCommissionsCount() throws Exception {
        configure(true, "1000", null, true, "2000", null);
        commission("suspended", "9000000", 40);
        commission("waived", "9000000", 40);
        commission("settled", "9000000", 40);
        assertThat(restrictionService.evaluate(owner.getId(), Instant.now()).level()).isZero();
        commission("adjusted", "1500", 1);
        assertThat(restrictionService.evaluate(owner.getId(), Instant.now()).level()).isEqualTo(1);
    }

    @Test
    @DisplayName("Muc tat khong ap dung; chi bat muc 2 thi chua den nguong muc 2 la khong han che")
    void disabledLevelsAreIgnored() throws Exception {
        configure(false, "1000", "1", false, "2000", "2");
        commission("due", "9000000", 40);
        assertThat(restrictionService.evaluate(owner.getId(), Instant.now()).level()).isZero();
        configure(false, "1000", "1", true, "10000000", null);
        assertThat(restrictionService.evaluate(owner.getId(), Instant.now()).level()).isZero();
        configure(false, "1000", "1", true, "5000000", null);
        assertThat(restrictionService.evaluate(owner.getId(), Instant.now()).level()).isEqualTo(2);
    }

    // -------------------------------------------------------------- luu muc va nhat ky

    @Test
    @DisplayName("Doi muc thi luu vao ho so va ghi nhat ky; khong doi muc thi khong ghi them; thanh toan xong thi tu go han che")
    void recomputeStoresLevelAuditsAndLifts() throws Exception {
        configure(true, "1000", null, true, "5000", null);
        BookingCommission debt = commission("due", "6000", 2);
        assertThat(restrictionService.recompute(owner.getId())).isTrue();
        assertThat(stored()).isEqualTo(2);
        assertThat(profileRepository.findById(owner.getId()).orElseThrow().getRestrictionChangedAt()).isNotNull();
        assertThat(restrictionService.recompute(owner.getId())).isFalse();
        long logs = auditLogRepository.findAll().stream()
                .filter(item -> "owner.restriction.change".equals(item.getAction()) && owner.getId().equals(item.getEntityId())).count();
        assertThat(logs).isEqualTo(1);
        var log = auditLogRepository.findAll().stream()
                .filter(item -> "owner.restriction.change".equals(item.getAction()) && owner.getId().equals(item.getEntityId())).findFirst().orElseThrow();
        assertThat(log.getOldData()).containsEntry("level", 0);
        assertThat(log.getNewData()).containsEntry("level", 2);

        debt.setStatus("settled");
        commissionRepository.saveAndFlush(debt);
        assertThat(restrictionService.recompute(owner.getId())).isTrue();
        assertThat(stored()).isZero();
        assertThat(auditLogRepository.findAll().stream()
                .filter(item -> "owner.restriction.change".equals(item.getAction()) && owner.getId().equals(item.getEntityId())).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Giam xuong muc 1 khi cong no con lai chi du nguong thap")
    void stepsDownToLevelOne() throws Exception {
        configure(true, "1000", null, true, "5000", null);
        BookingCommission big = commission("due", "6000", 2);
        restrictionService.recompute(owner.getId());
        assertThat(stored()).isEqualTo(2);
        big.setCommissionAmount(new BigDecimal("2000"));
        commissionRepository.saveAndFlush(big);
        restrictionService.recompute(owner.getId());
        assertThat(stored()).isEqualTo(1);
    }

    // -------------------------------------------------------------- thuc thi

    @Test
    @DisplayName("Muc 1: khong nhan booking moi (409 OWNER_RESTRICTED) nhung san van hien voi nguoi choi, booking da nhan van xu ly")
    void levelOneBlocksNewBookingsOnly() throws Exception {
        String pending = createBooking(playerToken, court, nextMonday().plusDays(40), LocalTime.of(9, 0));
        configure(true, "1000", null, true, "9000000", null);
        commission("due", "2000", 1);
        restrictionService.recompute(owner.getId());
        assertThat(stored()).isEqualTo(1);

        createBookingRequest().andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OWNER_RESTRICTED"));
        mockMvc.perform(get("/api/v1/venues/" + venue.getId())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/venues").param("q", "San Han Che")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("San Han Che"));
        // Booking da gui truoc khi bi han che van chap nhan duoc.
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + pending + "/accept").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Muc 2: san an khoi tim kiem, chi tiet, bao gia va lich trong; khong dat duoc; san cua chu san khac khong anh huong")
    void levelTwoHidesVenues() throws Exception {
        User other = persistOwner("other-owner");
        Venue otherVenue = persistVenue(other, "San Khac Khong Bi An");
        configure(true, "1000", null, true, "3000", null);
        commission("due", "4000", 1);
        restrictionService.recompute(owner.getId());
        assertThat(stored()).isEqualTo(2);

        mockMvc.perform(get("/api/v1/venues/" + venue.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/venues").param("q", "San Han Che")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/venues/" + venue.getId() + "/availability").param("date", nextMonday().toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/bookings/quote").contentType(MediaType.APPLICATION_JSON).content("""
                {"courtId":"%s","startTime":"%s","durationMinutes":90}
                """.formatted(court.getId(), nextMonday().atTime(LocalTime.of(9, 0)).atZone(ZONE).toInstant()))).andExpect(status().isNotFound());
        createBookingRequest().andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/venues/" + otherVenue.getId())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/venues").param("q", "San Khac Khong Bi An")).andExpect(jsonPath("$.totalElements").value(1));
        // Chu san van thay san cua minh trong khu quan ly.
        mockMvc.perform(get("/api/v1/owner/venues/" + venue.getId()).header("Authorization", bearer(ownerToken))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Het han che: san hien lai va dat duoc ngay sau khi tinh lai")
    void liftingRestoresBooking() throws Exception {
        configure(true, "1000", null, true, "3000", null);
        BookingCommission debt = commission("due", "4000", 1);
        restrictionService.recompute(owner.getId());
        createBookingRequest().andExpect(status().isNotFound());
        debt.setStatus("settled");
        commissionRepository.saveAndFlush(debt);
        restrictionService.recompute(owner.getId());
        mockMvc.perform(get("/api/v1/venues/" + venue.getId())).andExpect(status().isOk());
        createBookingRequest().andExpect(status().isCreated());
    }

    // -------------------------------------------------------------- doi nguong, man cong no, phan quyen

    @Test
    @DisplayName("Quan tri vien doi nguong thi ap dung ngay cho chu san; tat muc thi go han che ngay")
    void changingThresholdsAppliesImmediately() throws Exception {
        commission("due", "2000000", 20);
        assertThat(stored()).isZero();
        String on = """
                {"levels":[{"level":1,"enabled":true,"minOverdueAmount":1000000,"minOverdueDays":null},
                           {"level":2,"enabled":false,"minOverdueAmount":3000000,"minOverdueDays":null}]}""";
        mockMvc.perform(put("/api/v1/admin/fee-policies/debt-restrictions").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(on)).andExpect(status().isOk());
        assertThat(stored()).isEqualTo(1);
        String off = on.replace("\"level\":1,\"enabled\":true", "\"level\":1,\"enabled\":false");
        mockMvc.perform(put("/api/v1/admin/fee-policies/debt-restrictions").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(off)).andExpect(status().isOk());
        assertThat(stored()).isZero();
    }

    @Test
    @DisplayName("Man cong no cua chu san hien muc han che, so ngay qua han, ly do va cac nguong da cau hinh")
    void ownerDebtShowsRestriction() throws Exception {
        mockMvc.perform(get("/api/v1/owner/commission-debt").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.restriction.level").value(0))
                .andExpect(jsonPath("$.restriction.levels.length()").value(2))
                .andExpect(jsonPath("$.restriction.levels[0].enabled").value(false));
        configure(true, "1000", "3", true, null, "30");
        commission("due", "4000", 5);
        restrictionService.recompute(owner.getId());
        mockMvc.perform(get("/api/v1/owner/commission-debt").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restriction.level").value(1))
                .andExpect(jsonPath("$.restriction.byAmount").value(true))
                .andExpect(jsonPath("$.restriction.byDays").value(true))
                .andExpect(jsonPath("$.restriction.overdueDays").value(5))
                .andExpect(jsonPath("$.restriction.overdueAmount").value(4000))
                .andExpect(jsonPath("$.restriction.levels[0].minOverdueAmount").value(1000))
                .andExpect(jsonPath("$.restriction.levels[1].minOverdueDays").value(30));
        mockMvc.perform(get("/api/v1/owner/commission-debt").header("Authorization", bearer(loginAs(persistUser("p", Role.PLAYER)))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Chu san chua co ho so hoac khong co cong no: tinh lai khong loi")
    void recomputeAllIsSafe() {
        User noProfile = persistUser("khong-ho-so", Role.OWNER);
        assertThat(restrictionService.recompute(noProfile.getId())).isFalse();
        assertThat(restrictionService.levelOf(noProfile.getId())).isZero();
        assertThat(restrictionService.recomputeAll()).isGreaterThanOrEqualTo(0);
    }
}
