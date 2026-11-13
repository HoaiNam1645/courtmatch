package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.system.AuditLog;
import com.courtly.domain.system.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.1 - Dang nhap bang tai khoan chu san do quan tri vien cap. */
class OwnerAccountControllerTest extends OwnerApiTestBase {

    private static final String CHANGE_PASSWORD = "/api/v1/owner/account/password";
    private static final String NEW_PASSWORD = "SanMoi@2026";

    @Autowired private AuditLogRepository auditLogRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        ensureRoles();
        owner = persistOwner("owner-login");
    }

    @Test
    @DisplayName("Chu san dang nhap duoc, response co vai tro owner va co doi mat khau")
    void ownerLoginReturnsOwnerRole() throws Exception {
        owner.setPasswordChangeRequired(true);
        userRepository.saveAndFlush(owner);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailOrPhone":"%s","password":"%s"}
                                """.formatted(owner.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.roles[0]").value("owner"))
                .andExpect(jsonPath("$.user.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("Chu san bi khoa khong dang nhap duoc")
    void bannedOwnerCannotLogin() throws Exception {
        owner.setStatus(UserStatus.BANNED);
        userRepository.saveAndFlush(owner);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailOrPhone":"%s","password":"%s"}
                                """.formatted(owner.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("Nguoi choi va nguoi chua dang nhap khong vao duoc API chu san")
    void nonOwnerIsRejected() throws Exception {
        User player = persistUser("player", Role.PLAYER);

        mockMvc.perform(post(CHANGE_PASSWORD)
                        .header("Authorization", bearer(loginAs(player)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody(PASSWORD, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post(CHANGE_PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody(PASSWORD, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Doi mat khau tam: go co bat buoc, ghi audit, dang nhap duoc bang mat khau moi")
    void changeTemporaryPassword() throws Exception {
        owner.setPasswordChangeRequired(true);
        userRepository.saveAndFlush(owner);
        String token = loginAs(owner);

        mockMvc.perform(post(CHANGE_PASSWORD)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody(PASSWORD, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(false))
                .andExpect(jsonPath("$.roles[0]").value("owner"));

        User reloaded = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(reloaded.isPasswordChangeRequired()).isFalse();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, reloaded.getPasswordHash())).isTrue();

        AuditLog audit = auditLogRepository.findAll().stream()
                .filter(log -> owner.getId().equals(log.getEntityId())
                        && "owner.password.change".equals(log.getAction()))
                .findFirst().orElseThrow();
        assertThat(audit.getNewData()).doesNotContainKey("password");

        String newToken = loginAs(owner.getEmail(), NEW_PASSWORD);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(newToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(false));
    }

    @Test
    @DisplayName("Doi mat khau: sai mat khau hien tai, trung mat khau cu, sai dinh dang, khong khop")
    void changePasswordValidation() throws Exception {
        String token = loginAs(owner);

        mockMvc.perform(post(CHANGE_PASSWORD)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody("SaiMatKhau1", NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INVALID"))
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists());

        mockMvc.perform(post(CHANGE_PASSWORD)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody(PASSWORD, PASSWORD, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_SAME_AS_OLD"))
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

        mockMvc.perform(post(CHANGE_PASSWORD)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody(PASSWORD, "khongcoso", "khongcoso")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

        mockMvc.perform(post(CHANGE_PASSWORD)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody(PASSWORD, NEW_PASSWORD, "KhacNhau@2026")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.passwordConfirmed").exists());

        mockMvc.perform(post(CHANGE_PASSWORD)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").exists())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

        // Mat khau van la mat khau cu sau tat ca lan bi tu choi.
        assertThat(passwordEncoder.matches(PASSWORD,
                userRepository.findById(owner.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Token con han nhung tai khoan da bi khoa thi khong doi mat khau duoc")
    void bannedAfterLoginIsRejected() throws Exception {
        String token = loginAs(owner);
        owner.setStatus(UserStatus.BANNED);
        userRepository.saveAndFlush(owner);

        mockMvc.perform(post(CHANGE_PASSWORD)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeBody(PASSWORD, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    private static String changeBody(String current, String next, String confirm) {
        return """
                {"currentPassword":"%s","newPassword":"%s","confirmPassword":"%s"}
                """.formatted(current, next, confirm);
    }
}
