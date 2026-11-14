package com.courtly.api.admin;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 2.3.28 - Quan ly tai khoan nguoi dung. */
class AdminUserManagementTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/users";

    private User admin;
    private String adminToken;
    private User player;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-users");
        adminToken = loginAs(admin);
        player = persistUser("player-managed", Role.PLAYER);
    }

    private ResultActions changeStatus(String token, User target, String status, String reason) throws Exception {
        return mockMvc.perform(post(PATH + "/" + target.getId() + "/status").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"%s\",\"reason\":%s}".formatted(status, reason == null ? "null" : "\"" + reason + "\"")));
    }

    @Test
    @DisplayName("Danh sach co phan trang, tim theo ten khong dau / email, loc theo vai tro va trang thai, kem dem theo trang thai")
    void listAndFilter() throws Exception {
        User owner = persistOwner("owner-managed");
        player.setFullName("Nguyễn Văn Quản Lý");
        userRepository.saveAndFlush(player);

        mockMvc.perform(get(PATH).param("q", "nguyen van quan ly").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(player.getId().toString()))
                .andExpect(jsonPath("$.content[0].roles[0]").value("player"))
                .andExpect(jsonPath("$.content[0].emailVerified").value(false));
        mockMvc.perform(get(PATH).param("q", owner.getEmail()).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].roles[0]").value("owner"));
        mockMvc.perform(get(PATH).param("role", "admin").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.content[*].id", hasItem(admin.getId().toString())))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(player.getId().toString()))));
        mockMvc.perform(get(PATH).param("size", "2").param("page", "1").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.statusCounts.active").isNumber())
                .andExpect(jsonPath("$.statusCounts.banned").isNumber())
                .andExpect(jsonPath("$.statusCounts.pending").isNumber());

        changeStatus(adminToken, player, "banned", "Vi pham noi quy").andExpect(status().isOk());
        mockMvc.perform(get(PATH).param("status", "banned").param("q", "Quản Lý").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("banned"));
        mockMvc.perform(get(PATH).param("q", "%").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Chi tiet tai khoan: vai tro, xac minh, lan thay doi trang thai gan nhat kem ly do va nguoi thuc hien")
    void detail() throws Exception {
        changeStatus(adminToken, player, "inactive", "Nguoi dung yeu cau tam ngung").andExpect(status().isOk());
        mockMvc.perform(get(PATH + "/" + player.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(player.getEmail()))
                .andExpect(jsonPath("$.roles[0]").value("player"))
                .andExpect(jsonPath("$.status").value("inactive"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.lastStatusChange.status").value("inactive"))
                .andExpect(jsonPath("$.lastStatusChange.reason").value("Nguoi dung yeu cau tam ngung"))
                .andExpect(jsonPath("$.lastStatusChange.actor").value(admin.getFullName()));
        mockMvc.perform(get(PATH + "/" + java.util.UUID.randomUUID()).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/abc").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Khoa roi kich hoat lai; tai khoan bi khoa khong dang nhap duoc va token cu cung bi chan")
    void banAndReactivate() throws Exception {
        String playerToken = loginAs(player);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(playerToken))).andExpect(status().isOk());

        changeStatus(adminToken, player, "banned", "Gian lan dat san").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("banned"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailOrPhone\":\"%s\",\"password\":\"%s\"}".formatted(player.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(playerToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        // Duyet san cong khai van mo ke ca khi con giu token cu.
        mockMvc.perform(get("/api/v1/venues").header("Authorization", bearer(playerToken))).andExpect(status().isOk());

        changeStatus(adminToken, player, "active", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("active"));
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(playerToken))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Khoa / ngung bat buoc co ly do; trang thai khong hop le, trung trang thai, tai khoan chua xac minh bi chan")
    void statusRules() throws Exception {
        changeStatus(adminToken, player, "banned", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        changeStatus(adminToken, player, "banned", "ab").andExpect(status().isBadRequest());
        changeStatus(adminToken, player, "pending", "Khong hop le").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
        changeStatus(adminToken, player, "xyz", "Khong hop le").andExpect(status().isBadRequest());
        mockMvc.perform(post(PATH + "/" + player.getId() + "/status").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        changeStatus(adminToken, player, "active", null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_STATE_INVALID"));

        player.setStatus(UserStatus.PENDING);
        userRepository.saveAndFlush(player);
        changeStatus(adminToken, player, "active", null).andExpect(status().isConflict());
        changeStatus(adminToken, player, "banned", "Tai khoan gia mao").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Khong tu doi trang thai chinh minh (de khong tu khoa quan tri)")
    void cannotChangeOwnStatus() throws Exception {
        changeStatus(adminToken, admin, "banned", "Tu khoa").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_STATE_INVALID"));
        changeStatus(adminToken, admin, "inactive", "Tu ngung").andExpect(status().isConflict());
        mockMvc.perform(get(PATH + "/" + admin.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    @DisplayName("Chi quan tri vien dang hoat dong co quyen user.manage moi dung duoc; nguoi choi, chu san 403; chua dang nhap 401")
    void accessControl() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(player)))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(persistOwner("o")))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(PATH + "/" + player.getId() + "/status").header("Authorization", bearer(adminToken))).andExpect(status().is4xxClientError());

        // Quan tri vien bi khoa sau khi dang nhap: token cu khong dung duoc nua.
        User second = persistAdmin("admin-second");
        String secondToken = loginAs(second);
        mockMvc.perform(get(PATH).header("Authorization", bearer(secondToken))).andExpect(status().isOk());
        changeStatus(adminToken, second, "banned", "Vi pham").andExpect(status().isOk());
        mockMvc.perform(get(PATH).header("Authorization", bearer(secondToken))).andExpect(status().isForbidden());
    }
}
