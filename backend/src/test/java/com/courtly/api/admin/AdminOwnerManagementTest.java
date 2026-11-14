package com.courtly.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.VerificationStatus;
import com.courtly.domain.account.CourtOwnerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.venue.Venue;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 2.3.32 - Quan ly tai khoan chu san duy nhat. */
class AdminOwnerManagementTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/owner";

    private User admin;
    private String adminToken;
    private User owner;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-owner");
        adminToken = loginAs(admin);
        owner = persistOwner("owner-single");
        keepOnlyOwner(owner);
    }

    private ResultActions review(String token, String body) throws Exception {
        return mockMvc.perform(post(PATH + "/verification").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("Xem chu san duy nhat: tai khoan, ho so, thong tin nhan thanh toan, so san theo trang thai duyet va booking dang xu ly")
    void detail() throws Exception {
        Venue pending = persistVenue(owner, "San Cho Duyet");
        pending.setApprovalStatus(ApprovalStatus.PENDING);
        venueRepository.saveAndFlush(pending);
        Venue approved = persistVenue(owner, "San Da Duyet");
        createBooking(loginAs(persistUser("player-owner-detail", Role.PLAYER)), approved.getCourts().getFirst(), nextMonday(), LocalTime.of(9, 0));

        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner.id").value(owner.getId().toString()))
                .andExpect(jsonPath("$.owner.roles[0]").value("owner"))
                .andExpect(jsonPath("$.owner.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.profile.businessName").value("Ho kinh doanh owner-single"))
                .andExpect(jsonPath("$.profile.bankName").value("MB Bank"))
                .andExpect(jsonPath("$.profile.verificationStatus").value("verified"))
                .andExpect(jsonPath("$.overview.venuesByApproval.pending").value(1))
                .andExpect(jsonPath("$.overview.venuesByApproval.approved").value(1))
                .andExpect(jsonPath("$.overview.venuesByApproval.rejected").value(0))
                .andExpect(jsonPath("$.overview.outstandingCommission").value(0))
                .andExpect(jsonPath("$.overview.activeBookings").value(1));
    }

    @Test
    @DisplayName("Chua co chu san: tra owner null, khong loi")
    void noOwner() throws Exception {
        keepOnlyOwner(null);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.owner").doesNotExist()).andExpect(jsonPath("$.profile").doesNotExist());
        review(adminToken, "{\"status\":\"verified\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Quyet dinh xac minh: tu choi / dat lai can ly do, xac nhan xoa ly do, ghi nguoi quyet dinh")
    void reviewVerification() throws Exception {
        review(adminToken, "{\"status\":\"rejected\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        review(adminToken, "{\"status\":\"rejected\",\"reason\":\"ab\"}").andExpect(status().isBadRequest());
        review(adminToken, "{\"status\":\"rejected\",\"reason\":\"Thieu giay phep kinh doanh\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.verificationStatus").value("rejected"))
                .andExpect(jsonPath("$.profile.verificationNote").value("Thieu giay phep kinh doanh"))
                .andExpect(jsonPath("$.profile.verifiedBy").value(admin.getFullName()))
                .andExpect(jsonPath("$.profile.verifiedAt").doesNotExist());
        review(adminToken, "{\"status\":\"verified\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.verificationStatus").value("verified"))
                .andExpect(jsonPath("$.profile.verificationNote").doesNotExist())
                .andExpect(jsonPath("$.profile.verifiedAt").exists());
        review(adminToken, "{\"status\":\"verified\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OWNER_ACCOUNT_INVALID"));
        review(adminToken, "{\"status\":\"pending\",\"reason\":\"Can bo sung ho so\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.verificationStatus").value("pending"));
        CourtOwnerProfile saved = ownerProfileRepository.findById(owner.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(VerificationStatus.PENDING, saved.getVerificationStatus());
        review(adminToken, "{\"status\":\"xyz\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
        review(adminToken, "{}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Can quyen owner.verify; chu san va nguoi choi 403; chua dang nhap 401")
    void accessControl() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(owner)))).andExpect(status().isForbidden());
        review(loginAs(owner), "{\"status\":\"verified\"}").andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(persistUser("p", Role.PLAYER))))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "owner.verify".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        review(adminToken, "{\"status\":\"verified\"}").andExpect(status().isForbidden());
    }
}
