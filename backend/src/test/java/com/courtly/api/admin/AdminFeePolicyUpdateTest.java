package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.FeeType;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.finance.DebtRestrictionLevel;
import com.courtly.domain.finance.DebtRestrictionLevelRepository;
import com.courtly.domain.finance.PlatformFeeConfig;
import com.courtly.domain.finance.PlatformFeeConfigRepository;
import com.courtly.domain.system.AuditLog;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.service.finance.CommissionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.40 - Cap nhat phi nen tang va nguong han che cong no. */
class AdminFeePolicyUpdateTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/fee-policies";

    @Autowired private PlatformFeeConfigRepository configRepository;
    @Autowired private DebtRestrictionLevelRepository restrictionRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingCommissionRepository commissionRepository;
    @Autowired private CommissionService commissionService;
    @Autowired private AuditLogRepository auditLogRepository;

    private User admin;
    private String adminToken;
    private PlatformFeeConfig active;
    private PlatformFeeConfig old;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-fee-update");
        adminToken = loginAs(admin);
        configRepository.findAll().forEach(config -> {
            config.setStatus(ActiveStatus.INACTIVE);
            if (config.getEffectiveTo() == null) {
                config.setEffectiveTo(config.getEffectiveFrom().plusSeconds(1));
            }
        });
        configRepository.flush();
        old = policy("Phi cu", FeeType.FIXED, "15000", ActiveStatus.INACTIVE, 10);
        active = policy("Phi hien tai", FeeType.PERCENTAGE, "10", ActiveStatus.ACTIVE, 1);
    }

    private PlatformFeeConfig policy(String name, FeeType type, String value, ActiveStatus status, int daysAgo) {
        PlatformFeeConfig config = new PlatformFeeConfig();
        config.setId(UUID.randomUUID());
        config.setName(name);
        config.setFeeType(type);
        config.setFeeValue(new BigDecimal(value));
        config.setStatus(status);
        config.setEffectiveFrom(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        config.setCreatedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        config.setCreatedBy(admin);
        if (status == ActiveStatus.INACTIVE) {
            config.setEffectiveTo(Instant.now().minus(daysAgo - 1, ChronoUnit.DAYS));
        }
        return configRepository.saveAndFlush(config);
    }

    private org.springframework.test.web.servlet.ResultActions update(UUID id, String json) throws Exception {
        return mockMvc.perform(put(PATH + "/" + id).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private org.springframework.test.web.servlet.ResultActions activate(UUID id) throws Exception {
        return mockMvc.perform(post(PATH + "/" + id + "/activate").header("Authorization", bearer(adminToken)));
    }

    private org.springframework.test.web.servlet.ResultActions restrictions(String token, String json) throws Exception {
        return mockMvc.perform(put(PATH + "/debt-restrictions").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String name, String type, String value) {
        return "{\"name\":\"" + name + "\",\"feeType\":\"" + type + "\",\"feeValue\":" + value + "}";
    }

    private List<PlatformFeeConfig> actives() {
        return configRepository.findAll().stream().filter(config -> config.getStatus() == ActiveStatus.ACTIVE).toList();
    }

    private AuditLog audit(String action) {
        return auditLogRepository.findAll().stream()
                .filter(item -> action.equals(item.getAction()) && item.getActor() != null && admin.getId().equals(item.getActor().getId()))
                .findFirst().orElseThrow();
    }

    // ----------------------------------------------------------------- sua chinh sach

    @Test
    @DisplayName("Sua ten, loai phi, gia tri chinh sach dang ap dung: van la chinh sach active, ghi nhat ky truoc va sau")
    void updatesActivePolicy() throws Exception {
        update(active.getId(), body("Phi dieu chinh", "fixed", "25000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active.id").value(active.getId().toString()))
                .andExpect(jsonPath("$.active.name").value("Phi dieu chinh"))
                .andExpect(jsonPath("$.active.feeType").value("fixed"))
                .andExpect(jsonPath("$.active.feeValue").value(25000));
        PlatformFeeConfig saved = configRepository.findById(active.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(saved.getFeeValue()).isEqualByComparingTo("25000");
        assertThat(actives()).hasSize(1);
        AuditLog log = audit("fee.update");
        assertThat(log.getEntityId()).isEqualTo(active.getId());
        assertThat(log.getOldData()).containsEntry("name", "Phi hien tai").containsEntry("feeType", "percentage");
        assertThat(log.getNewData()).containsEntry("name", "Phi dieu chinh").containsEntry("feeType", "fixed");
    }

    @Test
    @DisplayName("Sua chinh sach cu khong lam doi chinh sach dang ap dung va khong doi trang thai chinh sach cu")
    void updatesOldPolicy() throws Exception {
        update(old.getId(), body("Phi cu doi ten", "fixed", "18000")).andExpect(status().isOk())
                .andExpect(jsonPath("$.active.id").value(active.getId().toString()))
                .andExpect(jsonPath("$.active.name").value("Phi hien tai"));
        PlatformFeeConfig saved = configRepository.findById(old.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Phi cu doi ten");
        assertThat(saved.getStatus()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(saved.getEffectiveTo()).isNotNull();
    }

    @Test
    @DisplayName("Doi gia tri khong lam doi hoa hong da ghi nhan; hoa hong ghi nhan sau do dung gia tri moi")
    void snapshotsKeepOldValue() throws Exception {
        User owner = persistOwner("owner-fee-update");
        Venue venue = persistVenue(owner, "San Doi Phi");
        String player = loginAs(persistUser("player-fee-update", Role.PLAYER));
        Booking first = booking(player, venue, 0);
        Booking second = booking(player, venue, 1);
        commissionService.createForCompletedBooking(first, Instant.now());
        BigDecimal recorded = commissionRepository.findByBookingId(first.getId()).orElseThrow().getCommissionAmount();

        update(active.getId(), body("Phi tang", "percentage", "30")).andExpect(status().isOk());
        commissionService.createForCompletedBooking(second, Instant.now());

        BookingCommission unchanged = commissionRepository.findByBookingId(first.getId()).orElseThrow();
        assertThat(unchanged.getCommissionAmount()).isEqualByComparingTo(recorded);
        assertThat(unchanged.getFeeValue()).isEqualByComparingTo("10");
        BookingCommission fresh = commissionRepository.findByBookingId(second.getId()).orElseThrow();
        assertThat(fresh.getFeeValue()).isEqualByComparingTo("30");
        assertThat(fresh.getCommissionAmount()).isEqualByComparingTo(second.getTotalAmount().multiply(new BigDecimal("0.30")));
    }

    private Booking booking(String playerToken, Venue venue, int dayOffset) throws Exception {
        String id = createBooking(playerToken, venue.getCourts().getFirst(), nextMonday().plusDays(dayOffset), LocalTime.of(9, 0));
        return bookingRepository.findById(UUID.fromString(id)).orElseThrow();
    }

    @Test
    @DisplayName("Khong thay doi gi thi 409; gia tri khong hop le 400; khong ton tai 404; id sai 400 va khong ghi gi")
    void updateValidation() throws Exception {
        update(active.getId(), body("Phi hien tai", "percentage", "10.00")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEE_POLICY_INVALID"));
        update(active.getId(), body("Phi hien tai", "percentage", "100.5")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.feeValue").exists());
        update(active.getId(), body("Phi hien tai", "percentage", "-1")).andExpect(status().isBadRequest());
        update(active.getId(), body("Phi hien tai", "fixed", "10000001")).andExpect(status().isBadRequest());
        update(active.getId(), body("Phi hien tai", "percentage", "1.234")).andExpect(status().isBadRequest());
        update(active.getId(), body("Phi hien tai", "weekly", "5")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.feeType").exists());
        update(active.getId(), body("A", "percentage", "5")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        update(active.getId(), body("   ", "percentage", "5")).andExpect(status().isBadRequest());
        update(active.getId(), "{\"name\":\"Phi\",\"feeType\":\"fixed\"}").andExpect(status().isBadRequest());
        update(UUID.randomUUID(), body("Phi khong co", "percentage", "5")).andExpect(status().isNotFound());
        mockMvc.perform(put(PATH + "/abc").header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                .content(body("Phi", "percentage", "5"))).andExpect(status().isBadRequest());
        PlatformFeeConfig saved = configRepository.findById(active.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Phi hien tai");
        assertThat(saved.getFeeValue()).isEqualByComparingTo("10");
        assertThat(auditLogRepository.findAll().stream().filter(item -> "fee.update".equals(item.getAction())
                && admin.getId().equals(item.getActor().getId()))).isEmpty();
    }

    // ----------------------------------------------------------------- kich hoat lai

    @Test
    @DisplayName("Kich hoat lai chinh sach cu: no co hieu luc tu bay gio, chinh sach dang active bi dong, luon chi mot active")
    void activatesOldPolicy() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        activate(old.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.active.id").value(old.getId().toString()))
                .andExpect(jsonPath("$.active.status").value("active"))
                .andExpect(jsonPath("$.active.effectiveTo").doesNotExist())
                .andExpect(jsonPath("$.policies[?(@.id=='" + active.getId() + "')].status").value(contains("inactive")));
        PlatformFeeConfig reactivated = configRepository.findById(old.getId()).orElseThrow();
        PlatformFeeConfig closed = configRepository.findById(active.getId()).orElseThrow();
        assertThat(actives()).hasSize(1);
        assertThat(reactivated.getEffectiveFrom()).isAfter(before);
        assertThat(reactivated.getEffectiveTo()).isNull();
        assertThat(closed.getStatus()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(closed.getEffectiveTo()).isNotNull().isAfter(closed.getEffectiveFrom());
        AuditLog log = audit("fee.activate");
        assertThat(log.getEntityId()).isEqualTo(old.getId());
        assertThat(log.getOldData()).containsEntry("name", "Phi hien tai");
        assertThat(log.getNewData()).containsEntry("name", "Phi cu").containsEntry("status", "active");
    }

    @Test
    @DisplayName("Chinh sach dang ap dung khong kich hoat lai duoc (409); khong the de he thong khong con chinh sach active")
    void cannotActivateActiveOrLeaveNone() throws Exception {
        activate(active.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FEE_POLICY_INVALID"));
        assertThat(actives()).hasSize(1);
        assertThat(actives().getFirst().getId()).isEqualTo(active.getId());
        activate(UUID.randomUUID()).andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "/abc/activate").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Chuyen qua lai nhieu lan van chi mot active; lich su dung thu tu; kich hoat khi he thong khong co chinh sach active")
    void activatesBackAndForth() throws Exception {
        activate(old.getId()).andExpect(status().isOk());
        activate(active.getId()).andExpect(status().isOk()).andExpect(jsonPath("$.active.id").value(active.getId().toString()));
        activate(old.getId()).andExpect(status().isOk());
        assertThat(actives()).hasSize(1);

        configRepository.findAll().forEach(config -> {
            config.setStatus(ActiveStatus.INACTIVE);
            if (config.getEffectiveTo() == null) {
                config.setEffectiveTo(Instant.now().plusSeconds(1));
            }
        });
        configRepository.flush();
        activate(active.getId()).andExpect(status().isOk()).andExpect(jsonPath("$.active.id").value(active.getId().toString()));
        assertThat(actives()).hasSize(1);
    }

    // ----------------------------------------------------------------- nguong han che

    private static String levels(String level1, String level2) {
        return "{\"levels\":[" + level1 + "," + level2 + "]}";
    }

    private static String level(int number, boolean enabled, String amount, String days) {
        return "{\"level\":" + number + ",\"enabled\":" + enabled + ",\"minOverdueAmount\":" + amount + ",\"minOverdueDays\":" + days + "}";
    }

    @Test
    @DisplayName("Luu nguong 2 muc: ap dung vao cau hinh, ghi nguoi sua va nhat ky; hien lai o man xem")
    void savesRestrictions() throws Exception {
        restrictions(adminToken, levels(level(1, true, "1000000", "7"), level(2, true, "3000000", "null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restrictions[0].enabled").value(true))
                .andExpect(jsonPath("$.restrictions[0].minOverdueAmount").value(1000000))
                .andExpect(jsonPath("$.restrictions[0].minOverdueDays").value(7))
                .andExpect(jsonPath("$.restrictions[0].updatedBy").value(admin.getFullName()))
                .andExpect(jsonPath("$.restrictions[1].enabled").value(true))
                .andExpect(jsonPath("$.restrictions[1].minOverdueAmount").value(3000000))
                .andExpect(jsonPath("$.restrictions[1].minOverdueDays").doesNotExist());
        DebtRestrictionLevel second = restrictionRepository.findById((short) 2).orElseThrow();
        assertThat(second.isEnabled()).isTrue();
        assertThat(second.getMinOverdueDays()).isNull();
        assertThat(second.getUpdatedBy().getId()).isEqualTo(admin.getId());
        AuditLog log = audit("fee.restriction.update");
        assertThat(log.getNewData()).containsKeys("level1", "level2");
        assertThat(log.getOldData()).containsKeys("level1", "level2");
    }

    @Test
    @DisplayName("Tat mot muc van giu nguong; chi muc co thay doi moi duoc ghi nhat ky va nguoi sua")
    void partialChangeOnlyTouchesChangedLevel() throws Exception {
        restrictions(adminToken, levels(level(1, true, "1000000", "7"), level(2, false, "5000000", "30"))).andExpect(status().isOk());
        Instant levelTwoStamp = restrictionRepository.findById((short) 2).orElseThrow().getUpdatedAt();
        restrictions(adminToken, levels(level(1, false, "1000000", "7"), level(2, false, "5000000", "30"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.restrictions[0].enabled").value(false))
                .andExpect(jsonPath("$.restrictions[0].minOverdueAmount").value(1000000));
        assertThat(restrictionRepository.findById((short) 2).orElseThrow().getUpdatedAt()).isEqualTo(levelTwoStamp);
        AuditLog latest = auditLogRepository.findAll().stream().filter(item -> "fee.restriction.update".equals(item.getAction())
                && admin.getId().equals(item.getActor().getId())).reduce((first, last) -> last.getCreatedAt().isAfter(first.getCreatedAt()) ? last : first).orElseThrow();
        assertThat(latest.getNewData()).containsKey("level1").doesNotContainKey("level2");
    }

    @Test
    @DisplayName("Khong thay doi gi 409; thieu muc, trung muc, muc la, thieu body 400")
    void restrictionStructure() throws Exception {
        restrictions(adminToken, levels(level(1, true, "1000000", "7"), level(2, true, "3000000", "14"))).andExpect(status().isOk());
        restrictions(adminToken, levels(level(1, true, "1000000.00", "7"), level(2, true, "3000000", "14"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FEE_POLICY_INVALID"));
        restrictions(adminToken, "{\"levels\":[" + level(1, true, "1000000", "7") + "]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.levels").exists());
        restrictions(adminToken, levels(level(1, true, "1000000", "7"), level(1, true, "2000000", "14"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.levels").exists());
        restrictions(adminToken, levels(level(1, true, "1000000", "7"), level(3, true, "2000000", "14"))).andExpect(status().isBadRequest());
        restrictions(adminToken, "{\"levels\":[]}").andExpect(status().isBadRequest());
        restrictions(adminToken, "{}").andExpect(status().isBadRequest());
        restrictions(adminToken, "khong phai json").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Gia tri nguong khong hop le 400: <= 0, qua lon, thap phan nhieu, muc bat thieu nguong; khong luu gi")
    void restrictionValues() throws Exception {
        String valid2 = level(2, false, "5000000", "30");
        List<Instant> stamps = restrictionRepository.findAllByOrderByLevelAsc().stream().map(DebtRestrictionLevel::getUpdatedAt).toList();
        restrictions(adminToken, levels(level(1, true, "0", "7"), valid2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['level1.minOverdueAmount']").exists());
        restrictions(adminToken, levels(level(1, true, "-5", "7"), valid2)).andExpect(status().isBadRequest());
        restrictions(adminToken, levels(level(1, true, "100000000001", "7"), valid2)).andExpect(status().isBadRequest());
        restrictions(adminToken, levels(level(1, true, "1000.123", "7"), valid2)).andExpect(status().isBadRequest());
        restrictions(adminToken, levels(level(1, true, "1000000", "0"), valid2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['level1.minOverdueDays']").exists());
        restrictions(adminToken, levels(level(1, true, "1000000", "3651"), valid2)).andExpect(status().isBadRequest());
        restrictions(adminToken, levels(level(1, true, "null", "null"), valid2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['level1.enabled']").exists());
        restrictions(adminToken, levels(level(1, false, "null", "null"), level(2, true, "null", "null"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['level2.enabled']").exists());
        assertThat(restrictionRepository.findAllByOrderByLevelAsc().stream().map(DebtRestrictionLevel::getUpdatedAt).toList()).isEqualTo(stamps);
    }

    @Test
    @DisplayName("Muc 2 phai nghiem hon hoac bang muc 1 khi ca hai cung bat; nguong khac loai hoac mot muc tat thi khong so sanh")
    void level2MustBeStricter() throws Exception {
        restrictions(adminToken, levels(level(1, true, "3000000", "14"), level(2, true, "2000000", "30"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['level2.minOverdueAmount']").exists());
        restrictions(adminToken, levels(level(1, true, "1000000", "30"), level(2, true, "2000000", "14"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['level2.minOverdueDays']").exists());
        restrictions(adminToken, levels(level(1, true, "3000000", "14"), level(2, true, "3000000", "14"))).andExpect(status().isOk());
        restrictions(adminToken, levels(level(1, true, "3000000", "null"), level(2, true, "null", "5"))).andExpect(status().isOk());
        restrictions(adminToken, levels(level(1, false, "9000000", "60"), level(2, true, "1000000", "7"))).andExpect(status().isOk());
    }

    // ----------------------------------------------------------------- phan quyen

    @Test
    @DisplayName("Can quyen finance.fee.config cho ca ba thao tac; chu san, nguoi choi 403; chua dang nhap 401; bi tu choi thi khong doi gi")
    void access() throws Exception {
        String ownerToken = loginAs(persistOwner("o"));
        String playerToken = loginAs(persistUser("p", Role.PLAYER));
        for (String token : List.of(ownerToken, playerToken)) {
            mockMvc.perform(put(PATH + "/" + active.getId()).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                    .content(body("Khong", "percentage", "1"))).andExpect(status().isForbidden());
            mockMvc.perform(post(PATH + "/" + old.getId() + "/activate").header("Authorization", bearer(token))).andExpect(status().isForbidden());
            restrictions(token, levels(level(1, true, "1000000", "7"), level(2, true, "2000000", "14"))).andExpect(status().isForbidden());
        }
        mockMvc.perform(put(PATH + "/" + active.getId()).contentType(MediaType.APPLICATION_JSON).content(body("Khong", "percentage", "1")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH + "/" + old.getId() + "/activate")).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "finance.fee.config".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        update(active.getId(), body("Khong", "percentage", "1")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        activate(old.getId()).andExpect(status().isForbidden());
        restrictions(adminToken, levels(level(1, true, "1000000", "7"), level(2, true, "2000000", "14"))).andExpect(status().isForbidden());
        assertThat(actives().getFirst().getId()).isEqualTo(active.getId());
        assertThat(configRepository.findById(active.getId()).orElseThrow().getName()).isEqualTo("Phi hien tai");
    }
}
