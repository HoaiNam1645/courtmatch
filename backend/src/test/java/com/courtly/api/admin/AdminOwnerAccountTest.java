package com.courtly.api.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.system.AuditLog;
import com.courtly.domain.system.AuditLogRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 2.3.33 - Tao, kich hoat, khoa hoac dat lai thong tin dang nhap chu san. */
class AdminOwnerAccountTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/owner/account";

    @Autowired private AuditLogRepository auditLogRepository;

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-owner-account");
        adminToken = loginAs(admin);
    }

    private String body(String name, String email, String phone) {
        return "{\"fullName\":%s,\"email\":%s,\"phone\":%s}".formatted(q(name), q(email), q(phone));
    }

    private static String q(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private ResultActions create(String token, String json) throws Exception {
        return mockMvc.perform(post(PATH).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String uniqueEmail() {
        return "chu-san-" + java.util.UUID.randomUUID() + "@example.com";
    }

    @Test
    @DisplayName("Cap tai khoan chu san: mat khau tam hien mot lan, bat buoc doi mat khau, ho so cho xac minh, khong luu cache, khong ghi nhat ky ban ro")
    void createOwner() throws Exception {
        keepOnlyOwner(null);
        String email = uniqueEmail();
        String response = create(adminToken, body("Chu San Moi", email, "091 234 5678"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.temporaryPassword", matchesPattern("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#$%&*]).{12}$")))
                .andExpect(jsonPath("$.detail.owner.email").value(email))
                .andExpect(jsonPath("$.detail.owner.phone").value("0912345678"))
                .andExpect(jsonPath("$.detail.owner.status").value("active"))
                .andExpect(jsonPath("$.detail.owner.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.detail.owner.roles[0]").value("owner"))
                .andExpect(jsonPath("$.detail.profile.verificationStatus").value("pending"))
                .andExpect(jsonPath("$.detail.owner.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String temporaryPassword = objectMapper.readTree(response).get("temporaryPassword").asText();

        String ownerToken = loginAs(email, temporaryPassword);
        mockMvc.perform(get("/api/v1/owner/profile").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        // Mat khau tam khong xuat hien o nhat ky.
        List<AuditLog> logs = auditLogRepository.findAll().stream().filter(log -> "owner.account.create".equals(log.getAction())).toList();
        org.junit.jupiter.api.Assertions.assertFalse(logs.isEmpty());
        logs.forEach(log -> org.junit.jupiter.api.Assertions.assertFalse(String.valueOf(log.getNewData()).contains(temporaryPassword)));
    }

    @Test
    @DisplayName("Da co chu san thi tu choi tao them (409); email / so dien thoai trung, so dien thoai sai, thieu truong")
    void createValidation() throws Exception {
        persistOwner("owner-existing");
        create(adminToken, body("Chu San Hai", uniqueEmail(), "0912345678"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OWNER_ACCOUNT_INVALID"));

        keepOnlyOwner(null);
        User taken = persistUser("taken", Role.PLAYER);
        taken.setPhone("0987654321");
        userRepository.saveAndFlush(taken);
        create(adminToken, body("Chu San", taken.getEmail().toUpperCase(), "0912345678"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        create(adminToken, body("Chu San", uniqueEmail(), "0987654321"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHONE_ALREADY_EXISTS"));
        create(adminToken, body("Chu San", uniqueEmail(), "12345")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.phone").exists());
        create(adminToken, body("Chu San", "khong-phai-email", "0912345678")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
        create(adminToken, body("", uniqueEmail(), "0912345678")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.fullName").exists());
        create(adminToken, "{}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Khoa chu san: khong dang nhap, token cu khong vao duoc khu quan ly; kich hoat lai dung duoc; can ly do")
    void banAndActivate() throws Exception {
        User owner = persistOwner("owner-ban");
        keepOnlyOwner(owner);
        String ownerToken = loginAs(owner);
        mockMvc.perform(get("/api/v1/owner/profile").header("Authorization", bearer(ownerToken))).andExpect(status().isOk());

        mockMvc.perform(post(PATH + "/status").header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"banned\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        mockMvc.perform(post(PATH + "/status").header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"banned\",\"reason\":\"No hoa hong qua han\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.owner.status").value("banned"))
                .andExpect(jsonPath("$.owner.lastStatusChange.reason").value("No hoa hong qua han"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailOrPhone\":\"%s\",\"password\":\"%s\"}".formatted(owner.getEmail(), PASSWORD))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/owner/profile").header("Authorization", bearer(ownerToken))).andExpect(status().isForbidden());

        mockMvc.perform(post(PATH + "/status").header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"active\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.owner.status").value("active"));
        mockMvc.perform(get("/api/v1/owner/profile").header("Authorization", bearer(ownerToken))).andExpect(status().isOk());
        mockMvc.perform(post(PATH + "/status").header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"active\"}")).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Dat lai thong tin dang nhap: mat khau cu het hieu luc, token cu bi chan, mat khau tam moi dung duoc va bat buoc doi")
    void resetCredentials() throws Exception {
        User owner = persistOwner("owner-reset");
        keepOnlyOwner(owner);
        String oldToken = loginAs(owner);

        String response = mockMvc.perform(post(PATH + "/reset-credentials").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.detail.owner.passwordChangeRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        String temporaryPassword = objectMapper.readTree(response).get("temporaryPassword").asText();
        org.junit.jupiter.api.Assertions.assertNotEquals(PASSWORD, temporaryPassword);

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailOrPhone\":\"%s\",\"password\":\"%s\"}".formatted(owner.getEmail(), PASSWORD))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/owner/profile").header("Authorization", bearer(oldToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        String newToken = loginAs(owner.getEmail(), temporaryPassword);
        mockMvc.perform(get("/api/v1/owner/profile").header("Authorization", bearer(newToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        String second = objectMapper.readTree(mockMvc.perform(post(PATH + "/reset-credentials").header("Authorization", bearer(adminToken)))
                .andReturn().getResponse().getContentAsString()).get("temporaryPassword").asText();
        org.junit.jupiter.api.Assertions.assertNotEquals(temporaryPassword, second);
        List<AuditLog> logs = auditLogRepository.findAll().stream().filter(log -> "owner.credentials.reset".equals(log.getAction())).toList();
        logs.forEach(log -> org.junit.jupiter.api.Assertions.assertFalse(String.valueOf(log.getNewData()).contains(second)));
    }

    @Test
    @DisplayName("Chua co chu san thi khoa / dat lai 404; can quyen user.manage; chu san va nguoi choi 403; chua dang nhap 401")
    void accessControl() throws Exception {
        keepOnlyOwner(null);
        mockMvc.perform(post(PATH + "/reset-credentials").header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "/status").header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"inactive\",\"reason\":\"Thu\"}")).andExpect(status().isNotFound());

        User owner = persistOwner("owner-acl");
        keepOnlyOwner(owner);
        mockMvc.perform(post(PATH + "/reset-credentials").header("Authorization", bearer(loginAs(owner)))).andExpect(status().isForbidden());
        create(loginAs(persistUser("p", Role.PLAYER)), body("A B", uniqueEmail(), "0912345678")).andExpect(status().isForbidden());
        mockMvc.perform(post(PATH + "/reset-credentials")).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "user.manage".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(post(PATH + "/reset-credentials").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }
}
