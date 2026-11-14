package com.courtly.api.admin;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRoleRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 2.3.29 - Phan quyen vai tro nguoi dung. */
class AdminRoleAssignmentTest extends AdminApiTestBase {

    @Autowired private UserRoleRepository userRoleRepository;

    private User admin;
    private String adminToken;
    private User player;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-roles");
        adminToken = loginAs(admin);
        player = persistUser("player-roles", Role.PLAYER);
    }

    private ResultActions assign(String token, UUID userId, String role) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/users/" + userId + "/roles").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":%s}".formatted(role == null ? "null" : "\"" + role + "\"")));
    }

    private ResultActions remove(String token, UUID userId, String role) throws Exception {
        return mockMvc.perform(delete("/api/v1/admin/users/" + userId + "/roles/" + role).header("Authorization", bearer(token)));
    }

    /** Dev DB co san chu san seed; go vai tro owner cua ho (rollback cuoi test) de thu duong gan thanh cong. */
    private void clearOwnerHolders() {
        userRoleRepository.findAll().stream()
                .filter(userRole -> Role.OWNER.equals(userRole.getRole().getCode()))
                .forEach(userRoleRepository::delete);
        userRoleRepository.flush();
    }

    @Test
    @DisplayName("Danh sach vai tro kem so nguoi dung va so quyen")
    void listRoles() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roles").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[*].code", hasItem("player")))
                .andExpect(jsonPath("$.roles[*].code", hasItem("admin")))
                .andExpect(jsonPath("$.roles[?(@.code=='admin')].permissionCount").isNotEmpty())
                .andExpect(jsonPath("$.roles[?(@.code=='player')].userCount").isNotEmpty());
    }

    @Test
    @DisplayName("Gan vai tro: tai khoan co nhieu vai tro, vai tro moi vao token o lan dang nhap sau; gan trung bi chan")
    void assignRole() throws Exception {
        assign(adminToken, player.getId(), "staff").andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", contains("player", "staff")));
        String relogin = loginAs(player);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(relogin)))
                .andExpect(jsonPath("$.roles", contains("player", "staff")));
        assign(adminToken, player.getId(), "staff").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_ASSIGNMENT_INVALID"));
        assign(adminToken, player.getId(), "superuser").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.role").exists());
        assign(adminToken, player.getId(), null).andExpect(status().isBadRequest());
        assign(adminToken, UUID.randomUUID(), "staff").andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/admin/users/" + player.getId() + "/roles").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Chi mot chu san: da co chu san thi tu choi, chua co thi gan duoc va tao ho so chu san; nguoi thu hai bi chan")
    void singleOwner() throws Exception {
        assign(adminToken, player.getId(), "owner").andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.role").value("Da co mot tai khoan chu san"));

        clearOwnerHolders();
        assign(adminToken, player.getId(), "owner").andExpect(status().isOk()).andExpect(jsonPath("$.roles", contains("owner", "player")));
        org.junit.jupiter.api.Assertions.assertNotNull(ownerProfileRepository.findById(player.getId()).orElse(null));

        User another = persistUser("player-roles-2", Role.PLAYER);
        assign(adminToken, another.getId(), "owner").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Go vai tro: go duoc, khong go vai tro cuoi, khong tu go admin, khong go vai tro khong co; go admin co hieu luc ngay")
    void removeRole() throws Exception {
        assign(adminToken, player.getId(), "staff").andExpect(status().isOk());
        remove(adminToken, player.getId(), "staff").andExpect(status().isOk()).andExpect(jsonPath("$.roles", contains("player")));
        remove(adminToken, player.getId(), "staff").andExpect(status().isConflict());
        remove(adminToken, player.getId(), "player").andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.role").exists());
        remove(adminToken, player.getId(), "superuser").andExpect(status().isBadRequest());
        remove(adminToken, UUID.randomUUID(), "player").andExpect(status().isNotFound());

        assign(adminToken, admin.getId(), "player").andExpect(status().isOk());
        remove(adminToken, admin.getId(), "admin").andExpect(status().isConflict());

        User second = persistAdmin("admin-roles-second");
        String secondToken = loginAs(second);
        mockMvc.perform(get("/api/v1/admin/roles").header("Authorization", bearer(secondToken))).andExpect(status().isOk());
        assign(adminToken, second.getId(), "player").andExpect(status().isOk());
        remove(adminToken, second.getId(), "admin").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/roles").header("Authorization", bearer(secondToken))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Can quyen user.role.assign; nguoi choi 403; chua dang nhap 401")
    void accessControl() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roles").header("Authorization", bearer(loginAs(player)))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/roles")).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "user.role.assign".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get("/api/v1/admin/roles").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assign(adminToken, player.getId(), "staff").andExpect(status().isForbidden());
    }
}
