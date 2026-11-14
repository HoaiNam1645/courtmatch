package com.courtly.api.admin;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.system.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 2.3.30 - Quan ly quyen cua vai tro. */
class AdminPermissionManagementTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/permissions";

    @Autowired private AuditLogRepository auditLogRepository;

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-perms");
        adminToken = loginAs(admin);
    }

    private ResultActions update(String token, String role, String json) throws Exception {
        return mockMvc.perform(put(PATH + "/roles/" + role).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("Ma tran: danh muc quyen, quyen cua tung vai tro va quyen cot loi cua admin")
    void matrix() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[*].code", hasItem("user.manage")))
                .andExpect(jsonPath("$.permissions[0].name").exists())
                .andExpect(jsonPath("$.roles[?(@.code=='admin')].permissions[*]", hasItem("venue.approve")))
                .andExpect(jsonPath("$.protectedPermissions[0]").value("user.manage"))
                .andExpect(jsonPath("$.protectedPermissions[1]").value("user.role.assign"));
    }

    @Test
    @DisplayName("Bat va tat quyen cua vai tro: ma tran doi theo, ghi nhat ky phan them / bot; gui lai cung tap thi khong ghi them")
    void togglePermissions() throws Exception {
        long before = auditLogRepository.count();
        update(adminToken, "staff", "{\"permissions\":[\"court.block\",\"booking.review\",\"payment.view\",\"stats.system.view\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[?(@.code=='staff')].permissions[*]", hasItem("stats.system.view")));
        update(adminToken, "staff", "{\"permissions\":[\"court.block\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[?(@.code=='staff')].permissions[*]", not(hasItem("booking.review"))));
        long afterTwo = auditLogRepository.count();
        org.junit.jupiter.api.Assertions.assertEquals(before + 2, afterTwo);
        update(adminToken, "staff", "{\"permissions\":[\"court.block\"]}").andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(afterTwo, auditLogRepository.count());
        update(adminToken, "staff", "{\"permissions\":[]}").andExpect(status().isOk());
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.roles[?(@.code=='staff')].permissions[*]", not(hasItem("court.block"))));
    }

    @Test
    @DisplayName("Quyen cua admin: go quyen thuong duoc, go quyen cot loi bi chan (409)")
    void adminProtected() throws Exception {
        String keep = "\"user.manage\",\"user.role.assign\"";
        update(adminToken, "admin", "{\"permissions\":[" + keep + ",\"venue.approve\"]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[?(@.code=='admin')].permissions[*]", not(hasItem("owner.verify"))));
        update(adminToken, "admin", "{\"permissions\":[\"user.manage\",\"venue.approve\"]}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERMISSION_ASSIGNMENT_INVALID"));
        update(adminToken, "admin", "{\"permissions\":[]}").andExpect(status().isConflict());
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Kiem tra du lieu: quyen khong ton tai, ma rong, thieu danh sach, vai tro khong ton tai")
    void validation() throws Exception {
        update(adminToken, "staff", "{\"permissions\":[\"khong.ton.tai\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.permissions").exists());
        update(adminToken, "staff", "{\"permissions\":[\" \"]}").andExpect(status().isBadRequest());
        update(adminToken, "staff", "{}").andExpect(status().isBadRequest());
        update(adminToken, "staff", "{\"permissions\":\"court.block\"}").andExpect(status().isBadRequest());
        update(adminToken, "khong-co", "{\"permissions\":[]}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Can quyen user.role.assign; nguoi choi 403; chua dang nhap 401")
    void accessControl() throws Exception {
        String playerToken = loginAs(persistUser("player-perms", Role.PLAYER));
        mockMvc.perform(get(PATH).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        update(playerToken, "staff", "{\"permissions\":[]}").andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "user.role.assign".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        update(adminToken, "staff", "{\"permissions\":[]}").andExpect(status().isForbidden());
    }
}
