package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.FeeType;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.finance.PlatformFeeConfig;
import com.courtly.domain.finance.PlatformFeeConfigRepository;
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

/** 2.3.41 - Xem cau hinh phi nen tang. */
class AdminFeePolicyViewTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/fee-policies";

    @Autowired protected PlatformFeeConfigRepository configRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private CommissionService commissionService;

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-fee");
        adminToken = loginAs(admin);
    }

    protected PlatformFeeConfig policy(String name, FeeType type, String value, ActiveStatus status, int daysAgo) {
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
            config.setEffectiveTo(Instant.now().minus(Math.max(daysAgo - 1, 0), ChronoUnit.DAYS).plusSeconds(60));
        }
        return configRepository.saveAndFlush(config);
    }

    /** Dua he thong ve mot bo chinh sach dung duoc (rollback cuoi test). */
    protected void resetPolicies() {
        configRepository.findAll().forEach(config -> {
            config.setStatus(ActiveStatus.INACTIVE);
            if (config.getEffectiveTo() == null) {
                config.setEffectiveTo(Instant.now().plusSeconds(1));
            }
        });
        configRepository.flush();
    }

    @Test
    @DisplayName("Co chinh sach active: hien dung chinh sach dang ap dung va danh sach cu moi nhat truoc, kem nguoi tao")
    void showsActiveAndHistory() throws Exception {
        resetPolicies();
        PlatformFeeConfig oldest = policy("Phi cu nhat", FeeType.PERCENTAGE, "5", ActiveStatus.INACTIVE, 30);
        PlatformFeeConfig older = policy("Phi cu", FeeType.FIXED, "15000", ActiveStatus.INACTIVE, 10);
        PlatformFeeConfig active = policy("Phi hien tai", FeeType.PERCENTAGE, "12.5", ActiveStatus.ACTIVE, 1);

        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active.id").value(active.getId().toString()))
                .andExpect(jsonPath("$.active.name").value("Phi hien tai"))
                .andExpect(jsonPath("$.active.feeType").value("percentage"))
                .andExpect(jsonPath("$.active.feeValue").value(12.5))
                .andExpect(jsonPath("$.active.status").value("active"))
                .andExpect(jsonPath("$.active.createdBy").value(admin.getFullName()))
                .andExpect(jsonPath("$.active.effectiveTo").doesNotExist())
                .andExpect(jsonPath("$.policies[*].id", hasItem(oldest.getId().toString())))
                .andExpect(jsonPath("$.policies[0].id").value(active.getId().toString()))
                .andExpect(jsonPath("$.policies[1].id").value(older.getId().toString()))
                .andExpect(jsonPath("$.policies[1].feeType").value("fixed"))
                .andExpect(jsonPath("$.policies[1].status").value("inactive"))
                .andExpect(jsonPath("$.policies[1].effectiveTo").exists());
    }

    @Test
    @DisplayName("Moi chinh sach cho biet so hoa hong da ghi nhan va tong tien; chinh sach chua dung thi bang 0")
    void showsUsagePerPolicy() throws Exception {
        resetPolicies();
        PlatformFeeConfig unused = policy("Chua dung", FeeType.PERCENTAGE, "5", ActiveStatus.INACTIVE, 20);
        PlatformFeeConfig active = policy("Dang dung", FeeType.PERCENTAGE, "10", ActiveStatus.ACTIVE, 1);

        User owner = persistOwner("owner-fee");
        Venue venue = persistVenue(owner, "San Phi");
        String player = loginAs(persistUser("player-fee", Role.PLAYER));
        for (int index = 0; index < 2; index++) {
            String bookingId = createBooking(player, venue.getCourts().getFirst(), nextMonday().plusDays(index), LocalTime.of(9, 0));
            Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
            commissionService.createForCompletedBooking(booking, Instant.now());
        }

        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.active.commissionCount").value(2))
                .andExpect(jsonPath("$.active.commissionTotal").value(36000))
                .andExpect(jsonPath("$.policies[?(@.id=='" + unused.getId() + "')].commissionCount").value(contains(0)))
                .andExpect(jsonPath("$.policies[?(@.id=='" + unused.getId() + "')].commissionTotal").value(contains(0)));
        assertThat(active.getFeeValue()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("He thong chua co chinh sach active: active la null, van liet ke cac chinh sach da dung")
    void noActivePolicy() throws Exception {
        resetPolicies();
        PlatformFeeConfig only = policy("Da dung", FeeType.PERCENTAGE, "8", ActiveStatus.INACTIVE, 5);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").doesNotExist())
                .andExpect(jsonPath("$.policies[*].id", hasItem(only.getId().toString())));
    }

    @Test
    @DisplayName("Nguong han che cong no qua han: du hai muc, mac dinh chua bat, kem nguong so tien va so ngay")
    void showsDebtRestrictionLevels() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restrictions.length()").value(2))
                .andExpect(jsonPath("$.restrictions[0].level").value(1))
                .andExpect(jsonPath("$.restrictions[1].level").value(2))
                .andExpect(jsonPath("$.restrictions[0].enabled").isBoolean())
                .andExpect(jsonPath("$.restrictions[0].minOverdueAmount").isNumber())
                .andExpect(jsonPath("$.restrictions[0].minOverdueDays").isNumber())
                .andExpect(jsonPath("$.restrictions[1].minOverdueAmount").isNumber());
    }

    @Test
    @DisplayName("Can quyen finance.fee.config; chu san, nguoi choi 403; chua dang nhap 401")
    void access() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(persistOwner("o"))))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(persistUser("p", Role.PLAYER))))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "finance.fee.config".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }
}
