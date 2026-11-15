package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.courtly.domain.finance.PlatformFeeConfig;
import com.courtly.domain.finance.PlatformFeeConfigRepository;
import com.courtly.domain.system.AuditLog;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.service.finance.CommissionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.39 - Thiet lap phi nen tang. */
class AdminFeePolicyCreateTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/fee-policies";

    @Autowired private PlatformFeeConfigRepository configRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingCommissionRepository commissionRepository;
    @Autowired private CommissionService commissionService;
    @Autowired private AuditLogRepository auditLogRepository;

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-fee-create");
        adminToken = loginAs(admin);
    }

    private org.springframework.test.web.servlet.ResultActions create(String token, String json) throws Exception {
        return mockMvc.perform(post(PATH).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String body(String name, String type, String value) {
        return "{\"name\":" + (name == null ? "null" : "\"" + name + "\"") + ",\"feeType\":" + (type == null ? "null" : "\"" + type + "\"")
                + ",\"feeValue\":" + value + "}";
    }

    private List<PlatformFeeConfig> actives() {
        return configRepository.findAll().stream().filter(config -> config.getStatus() == ActiveStatus.ACTIVE).toList();
    }

    @Test
    @DisplayName("Tao chinh sach phan tram: hieu luc ngay, chinh sach cu chuyen inactive va dong hieu luc, luon chi con mot active")
    void createsAndReplacesActive() throws Exception {
        PlatformFeeConfig previous = actives().getFirst();
        create(adminToken, body("Phi thang 12", "percentage", "12.5"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active.name").value("Phi thang 12"))
                .andExpect(jsonPath("$.active.feeType").value("percentage"))
                .andExpect(jsonPath("$.active.feeValue").value(12.5))
                .andExpect(jsonPath("$.active.status").value("active"))
                .andExpect(jsonPath("$.active.createdBy").value(admin.getFullName()))
                .andExpect(jsonPath("$.active.effectiveTo").doesNotExist())
                .andExpect(jsonPath("$.active.commissionCount").value(0))
                .andExpect(jsonPath("$.policies[?(@.id=='" + previous.getId() + "')].status").value(org.hamcrest.Matchers.contains("inactive")))
                .andExpect(jsonPath("$.policies[?(@.id=='" + previous.getId() + "')].effectiveTo").value(org.hamcrest.Matchers.hasSize(1)));

        assertThat(actives()).hasSize(1);
        PlatformFeeConfig current = actives().getFirst();
        assertThat(current.getName()).isEqualTo("Phi thang 12");
        assertThat(current.getFeeValue()).isEqualByComparingTo("12.5");
        assertThat(current.getCreatedBy().getId()).isEqualTo(admin.getId());
        PlatformFeeConfig closed = configRepository.findById(previous.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(ActiveStatus.INACTIVE);
        assertThat(closed.getEffectiveTo()).isNotNull().isEqualTo(current.getEffectiveFrom());
        assertThat(closed.getEffectiveTo()).isAfter(closed.getEffectiveFrom());
    }

    @Test
    @DisplayName("Tao phi co dinh theo booking; nhieu lan lien tiep van chi mot active, lich su day du")
    void createsFixedAndChains() throws Exception {
        long before = configRepository.count();
        create(adminToken, body("Phi co dinh", "fixed", "15000")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.active.feeType").value("fixed")).andExpect(jsonPath("$.active.feeValue").value(15000));
        create(adminToken, body("Phi sau do", "percentage", "8")).andExpect(status().isCreated());
        create(adminToken, body("Phi cuoi", "percentage", "9.99")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.policies", hasSize((int) before + 3)))
                .andExpect(jsonPath("$.active.name").value("Phi cuoi"))
                .andExpect(jsonPath("$.policies[0].name").value("Phi cuoi"))
                .andExpect(jsonPath("$.policies[1].name").value("Phi sau do"))
                .andExpect(jsonPath("$.policies[2].name").value("Phi co dinh"));
        assertThat(actives()).hasSize(1);
    }

    @Test
    @DisplayName("Ten duoc cat khoang trang; gia tri 0 va bien 100% / 10.000.000d deu hop le")
    void acceptsBoundaryValues() throws Exception {
        create(adminToken, body("  Phi 0  ", "percentage", "0")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.active.name").value("Phi 0")).andExpect(jsonPath("$.active.feeValue").value(0));
        create(adminToken, body("Phi 100", "percentage", "100")).andExpect(status().isCreated());
        create(adminToken, body("Co dinh 0", "fixed", "0")).andExpect(status().isCreated());
        create(adminToken, body("Co dinh toi da", "fixed", "10000000")).andExpect(status().isCreated());
        create(adminToken, body("Thap phan", "percentage", "7.50")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.active.feeValue").value(7.5));
        create(adminToken, body("Hai so le", "percentage", "7.250")).andExpect(status().isCreated());
        assertThat(actives()).hasSize(1);
    }

    @Test
    @DisplayName("Gia tri khong hop le bi tu choi 400, chinh sach dang active khong bi anh huong")
    void rejectsInvalidValues() throws Exception {
        PlatformFeeConfig before = actives().getFirst();
        create(adminToken, body("Phi am", "percentage", "-1")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.feeValue").exists());
        create(adminToken, body("Qua 100", "percentage", "100.01")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.feeValue").exists());
        create(adminToken, body("Qua lon", "fixed", "10000001")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.feeValue").exists());
        create(adminToken, body("Ba so le", "percentage", "7.123")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.feeValue").exists());
        create(adminToken, body("Sai loai", "monthly", "5")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.feeType").exists());
        create(adminToken, body("Thieu loai", null, "5")).andExpect(status().isBadRequest());
        create(adminToken, "{\"name\":\"Thieu gia tri\",\"feeType\":\"fixed\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.feeValue").exists());
        create(adminToken, body(null, "fixed", "5")).andExpect(status().isBadRequest());
        create(adminToken, body("   ", "fixed", "5")).andExpect(status().isBadRequest());
        create(adminToken, body("A", "fixed", "5")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
        create(adminToken, body("x".repeat(151), "fixed", "5")).andExpect(status().isBadRequest());
        create(adminToken, "{\"name\":\"Gia tri chu\",\"feeType\":\"fixed\",\"feeValue\":\"abc\"}").andExpect(status().isBadRequest());
        create(adminToken, "khong phai json").andExpect(status().isBadRequest());

        assertThat(actives()).hasSize(1);
        assertThat(actives().getFirst().getId()).isEqualTo(before.getId());
        assertThat(configRepository.findById(before.getId()).orElseThrow().getEffectiveTo()).isNull();
    }

    @Test
    @DisplayName("Hoa hong da ghi nhan giu nguyen loai phi va gia tri; hoa hong ghi nhan sau do dung chinh sach moi")
    void snapshotsAreNotChanged() throws Exception {
        User owner = persistOwner("owner-snapshot");
        Venue venue = persistVenue(owner, "San Snapshot");
        String player = loginAs(persistUser("player-snapshot", Role.PLAYER));
        Booking first = booking(player, venue, 0);
        Booking second = booking(player, venue, 1);
        commissionService.createForCompletedBooking(first, Instant.now());
        BookingCommission recorded = commissionRepository.findByBookingId(first.getId()).orElseThrow();
        BigDecimal oldAmount = recorded.getCommissionAmount();
        FeeType oldType = recorded.getFeeType();
        BigDecimal oldValue = recorded.getFeeValue();

        create(adminToken, body("Phi moi 20%", "percentage", "20")).andExpect(status().isCreated());
        commissionService.createForCompletedBooking(second, Instant.now());

        BookingCommission unchanged = commissionRepository.findByBookingId(first.getId()).orElseThrow();
        assertThat(unchanged.getCommissionAmount()).isEqualByComparingTo(oldAmount);
        assertThat(unchanged.getFeeType()).isEqualTo(oldType);
        assertThat(unchanged.getFeeValue()).isEqualByComparingTo(oldValue);
        BookingCommission fresh = commissionRepository.findByBookingId(second.getId()).orElseThrow();
        assertThat(fresh.getFeeValue()).isEqualByComparingTo("20");
        assertThat(fresh.getCommissionAmount()).isEqualByComparingTo(second.getTotalAmount().multiply(new BigDecimal("0.20")));
        assertThat(fresh.getFeeConfig().getName()).isEqualTo("Phi moi 20%");
    }

    private Booking booking(String playerToken, Venue venue, int dayOffset) throws Exception {
        String id = createBooking(playerToken, venue.getCourts().getFirst(), nextMonday().plusDays(dayOffset), LocalTime.of(9, 0));
        return bookingRepository.findById(UUID.fromString(id)).orElseThrow();
    }

    @Test
    @DisplayName("He thong chua co chinh sach active: tao duoc chinh sach dau tien, khong co chinh sach cu de dong")
    void createsWhenNothingIsActive() throws Exception {
        configRepository.findAll().forEach(config -> {
            config.setStatus(ActiveStatus.INACTIVE);
            if (config.getEffectiveTo() == null) {
                config.setEffectiveTo(config.getEffectiveFrom().plusSeconds(1));
            }
        });
        configRepository.flush();
        create(adminToken, body("Phi khoi dong", "percentage", "10")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.active.name").value("Phi khoi dong"));
        assertThat(actives()).hasSize(1);
        AuditLog log = auditLogRepository.findAll().stream().filter(item -> "fee.create".equals(item.getAction())
                && admin.getId().equals(item.getActor().getId())).findFirst().orElseThrow();
        assertThat(log.getOldData()).isEmpty();
    }

    @Test
    @DisplayName("Nhat ky ghi chinh sach cu va moi: ten, loai phi, gia tri")
    void writesAuditLog() throws Exception {
        PlatformFeeConfig previous = actives().getFirst();
        create(adminToken, body("Phi co nhat ky", "fixed", "20000")).andExpect(status().isCreated());
        AuditLog log = auditLogRepository.findAll().stream().filter(item -> "fee.create".equals(item.getAction())
                && admin.getId().equals(item.getActor().getId())).findFirst().orElseThrow();
        assertThat(log.getEntityType()).isEqualTo("platform_fee_configs");
        assertThat(log.getOldData()).containsEntry("id", previous.getId().toString()).containsEntry("name", previous.getName());
        assertThat(log.getNewData()).containsEntry("name", "Phi co nhat ky").containsEntry("feeType", "fixed");
        assertThat(log.getEntityId()).isEqualTo(actives().getFirst().getId());
    }

    @Test
    @DisplayName("Can quyen finance.fee.config; chu san, nguoi choi 403; chua dang nhap 401; bi tu choi thi khong doi gi")
    void access() throws Exception {
        PlatformFeeConfig before = actives().getFirst();
        String valid = body("Khong duoc tao", "percentage", "5");
        create(loginAs(persistOwner("o")), valid).andExpect(status().isForbidden());
        create(loginAs(persistUser("p", Role.PLAYER)), valid).andExpect(status().isForbidden());
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "finance.fee.config".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        create(adminToken, valid).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(actives()).hasSize(1);
        assertThat(actives().getFirst().getId()).isEqualTo(before.getId());
    }
}
