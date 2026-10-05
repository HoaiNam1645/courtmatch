package com.courtly.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.config.PasswordResetProperties;
import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.PasswordResetRequest;
import com.courtly.domain.account.PasswordResetRequestRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.service.auth.OtpRateLimiter;
import com.courtly.service.mail.PasswordResetCodeIssued;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiem thu 2.1.5 quen va dat lai mat khau.
 *
 * <p>Ma xac minh khong bao gio ra khoi he thong qua API nen test khong doc duoc tu response.
 * Thay vao do bat su kien {@link PasswordResetCodeIssued} - dung cai ma MailService nhan.
 *
 * <p>MailService bi thay bang mock: test khong duoc gui email that. Thuc te su kien
 * AFTER_COMMIT cung khong chay vi test luon rollback, mock chi la chot chan thu hai.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetControllerTest {

    private static final String SEND = "/api/v1/auth/password-reset";
    private static final String VERIFY = "/api/v1/auth/password-reset/verify";
    private static final String CONFIRM = "/api/v1/auth/password-reset/confirm";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String OLD_PASSWORD = "Courtly@123";
    private static final String NEW_PASSWORD = "CourtlyMoi@456";

    @TestConfiguration
    static class CapturedCodesConfig {
        @Bean
        CapturedCodes capturedCodes() {
            return new CapturedCodes();
        }
    }

    /** Nghe su kien ngay khi phat (khong cho commit) de test lay duoc ma goc. */
    static class CapturedCodes {
        private final List<PasswordResetCodeIssued> events = new ArrayList<>();

        @EventListener
        void on(PasswordResetCodeIssued event) {
            events.add(event);
        }

        void clear() {
            events.clear();
        }

        Optional<String> latestFor(String email) {
            return events.stream()
                    .filter(event -> event.email().equals(email))
                    .reduce((first, second) -> second)
                    .map(PasswordResetCodeIssued::code);
        }

        int countFor(String email) {
            return (int) events.stream().filter(event -> event.email().equals(email)).count();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetRequestRepository resetRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OtpRateLimiter rateLimiter;

    @Autowired
    private PasswordResetProperties properties;

    @Autowired
    private CapturedCodes capturedCodes;

    @MockitoBean
    private com.courtly.service.mail.MailService mailService;

    private String email;

    @BeforeEach
    void setUp() {
        capturedCodes.clear();
        email = "reset-" + UUID.randomUUID() + "@example.com";
        rateLimiter.reset("reset", email);
    }

    // --- Buoc 1: gui ma ----------------------------------------------------

    @Test
    @DisplayName("Email da dang ky: tra 200, tao yeu cau va sinh ma 6 chu so")
    void sendCodeToRegisteredEmail() throws Exception {
        persistUser(email, UserStatus.ACTIVE);

        mockMvc.perform(sendRequest("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(properties.codeTtlMinutes() * 60))
                .andExpect(jsonPath("$.resendAfterSeconds").value(properties.resendCooldownSeconds()));

        assertThat(capturedCodes.latestFor(email)).hasValueSatisfying(
                code -> assertThat(code).matches("\\d{6}"));
        assertThat(resetRepository.findLatestPending(email)).isPresent();
    }

    @Test
    @DisplayName("Email chua dang ky: tra ve y het truong hop co that, khong tao yeu cau")
    void sendCodeToUnknownEmailLooksIdentical() throws Exception {
        mockMvc.perform(sendRequest("email", email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(properties.codeTtlMinutes() * 60));

        assertThat(capturedCodes.countFor(email)).isZero();
        assertThat(resetRepository.findLatestPending(email)).isEmpty();
    }

    @Test
    @DisplayName("Tai khoan bi khoa: van tra 200 nhung khong gui ma")
    void sendCodeToDisabledAccount() throws Exception {
        persistUser(email, UserStatus.BANNED);

        mockMvc.perform(sendRequest("email", email)).andExpect(status().isOk());

        assertThat(capturedCodes.countFor(email)).isZero();
    }

    @Test
    @DisplayName("Kenh SMS chua ho tro: 400 RESET_CHANNEL_UNSUPPORTED")
    void sendCodeByPhoneIsRejected() throws Exception {
        mockMvc.perform(sendRequest("phone", "0901234567"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_CHANNEL_UNSUPPORTED"));
    }

    @Test
    @DisplayName("Kenh khong nam trong danh sach: 400, khong lo ten class Java")
    void sendCodeWithUnknownChannel() throws Exception {
        mockMvc.perform(post(SEND).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"telegram\",\"destination\":\"a@b.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("java."))));
    }

    @Test
    @DisplayName("Thieu email: 400 VALIDATION_FAILED kem loi theo truong")
    void sendCodeWithoutDestination() throws Exception {
        mockMvc.perform(sendRequest("email", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.destination").exists());
    }

    @Test
    @DisplayName("Email sai dinh dang: 400 kem loi o truong destination")
    void sendCodeWithMalformedEmail() throws Exception {
        mockMvc.perform(sendRequest("email", "khong-phai-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.destination").exists());
    }

    @Test
    @DisplayName("Bam gui lai ngay: 429 kem so giay con phai doi")
    void resendTooSoon() throws Exception {
        persistUser(email, UserStatus.ACTIVE);
        mockMvc.perform(sendRequest("email", email)).andExpect(status().isOk());

        mockMvc.perform(sendRequest("email", email))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RESET_TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.fieldErrors.retryAfterSeconds").exists());
    }

    @Test
    @DisplayName("Gioi han gui ap dung ca voi email khong ton tai, tranh do danh sach email")
    void resendLimitAppliesToUnknownEmailToo() throws Exception {
        mockMvc.perform(sendRequest("email", email)).andExpect(status().isOk());

        mockMvc.perform(sendRequest("email", email))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RESET_TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("Email khong phan biet hoa thuong khi tim tai khoan")
    void sendCodeIsCaseInsensitive() throws Exception {
        persistUser(email, UserStatus.ACTIVE);

        mockMvc.perform(sendRequest("email", email.toUpperCase()))
                .andExpect(status().isOk());

        assertThat(capturedCodes.countFor(email)).isEqualTo(1);
    }

    // --- Buoc 2: xac minh ma -----------------------------------------------

    @Test
    @DisplayName("Ma dung: tra token dat mat khau, khong tra lai ma goc")
    void verifyWithCorrectCode() throws Exception {
        String code = requestCodeFor(email);

        String body = mockMvc.perform(verifyRequest(email, code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(properties.tokenTtlMinutes() * 60))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(code);
        assertThat(resetRepository.findLatestPending(email).orElseThrow().getVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("Ma sai: 400 va so lan thu duoc ghi lai, khong bi rollback")
    void verifyWithWrongCodeCountsAttempt() throws Exception {
        requestCodeFor(email);

        mockMvc.perform(verifyRequest(email, "000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));

        assertThat(resetRepository.findLatestPending(email).orElseThrow().getAttemptCount())
                .isEqualTo((short) 1);
    }

    @Test
    @DisplayName("Sai qua so lan cho phep: 429 va ma bi huy hoan toan")
    void verifyTooManyAttemptsKillsTheCode() throws Exception {
        String code = requestCodeFor(email);

        for (int attempt = 1; attempt < properties.maxAttempts(); attempt++) {
            mockMvc.perform(verifyRequest(email, "000000")).andExpect(status().isBadRequest());
        }
        mockMvc.perform(verifyRequest(email, "000000"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RESET_TOO_MANY_ATTEMPTS"));

        // Ma that cung khong con dung duoc nua.
        mockMvc.perform(verifyRequest(email, code)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Ma het han: 400 RESET_CODE_INVALID")
    void verifyExpiredCode() throws Exception {
        String code = requestCodeFor(email);
        PasswordResetRequest reset = resetRepository.findLatestPending(email).orElseThrow();
        reset.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        resetRepository.saveAndFlush(reset);

        mockMvc.perform(verifyRequest(email, code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));
    }

    @Test
    @DisplayName("Chua tung xin ma: 400 giong het truong hop nhap sai ma")
    void verifyWithoutAnyRequest() throws Exception {
        mockMvc.perform(verifyRequest(email, "123456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));
    }

    @Test
    @DisplayName("Ma khong phai 6 chu so: 400 VALIDATION_FAILED")
    void verifyWithMalformedCode() throws Exception {
        mockMvc.perform(verifyRequest(email, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.code").exists());
    }

    @Test
    @DisplayName("Dung lai ma da xac minh: 400, mot ma chi doi duoc mot token")
    void verifyTwiceWithSameCode() throws Exception {
        String code = requestCodeFor(email);
        mockMvc.perform(verifyRequest(email, code)).andExpect(status().isOk());

        mockMvc.perform(verifyRequest(email, code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_CODE_INVALID"));
    }

    @Test
    @DisplayName("Xin ma moi thi ma cu het hieu luc")
    void newCodeInvalidatesPreviousOne() throws Exception {
        String firstCode = requestCodeFor(email);
        rateLimiter.reset("reset", email);
        String secondCode = requestCodeFor(email);

        mockMvc.perform(verifyRequest(email, firstCode)).andExpect(status().isBadRequest());
        mockMvc.perform(verifyRequest(email, secondCode)).andExpect(status().isOk());
    }

    // --- Buoc 3: dat mat khau moi ------------------------------------------

    @Test
    @DisplayName("Token hop le: 204, dang nhap duoc bang mat khau moi va khong dung mat khau cu")
    void confirmChangesPassword() throws Exception {
        persistUser(email, UserStatus.ACTIVE);
        String token = verifiedTokenFor(email);

        mockMvc.perform(confirmRequest(token, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, NEW_PASSWORD)))
                .andExpect(status().isOk());
        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, OLD_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token chi dung duoc mot lan")
    void confirmTwiceWithSameToken() throws Exception {
        persistUser(email, UserStatus.ACTIVE);
        String token = verifiedTokenFor(email);

        mockMvc.perform(confirmRequest(token, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isNoContent());

        mockMvc.perform(confirmRequest(token, "KhacNua@789", "KhacNua@789"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Token bia dat hoac sai dinh dang: 400 RESET_TOKEN_INVALID")
    void confirmWithForgedToken() throws Exception {
        mockMvc.perform(confirmRequest("khong-phai-token", NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));

        mockMvc.perform(confirmRequest(UUID.randomUUID() + ".chuoi-bia-dat", NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Doan dung id yeu cau nhung sai phan bi mat: 400")
    void confirmWithRightIdButWrongSecret() throws Exception {
        persistUser(email, UserStatus.ACTIVE);
        String token = verifiedTokenFor(email);
        String requestId = token.split("\\.", 2)[0];

        mockMvc.perform(confirmRequest(requestId + ".sai-bi-mat", NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Token het han: 400 RESET_TOKEN_INVALID")
    void confirmWithExpiredToken() throws Exception {
        persistUser(email, UserStatus.ACTIVE);
        String token = verifiedTokenFor(email);
        PasswordResetRequest reset = resetRepository.findLatestPending(email).orElseThrow();
        reset.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        resetRepository.saveAndFlush(reset);

        mockMvc.perform(confirmRequest(token, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESET_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Mat khau moi trung mat khau cu: 400 PASSWORD_SAME_AS_OLD")
    void confirmWithSamePassword() throws Exception {
        persistUser(email, UserStatus.ACTIVE);
        String token = verifiedTokenFor(email);

        mockMvc.perform(confirmRequest(token, OLD_PASSWORD, OLD_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_SAME_AS_OLD"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("Mat khau qua ngan hoac thieu chu so: 400 VALIDATION_FAILED")
    void confirmWithWeakPassword() throws Exception {
        mockMvc.perform(confirmRequest("a.b", "abc", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        mockMvc.perform(confirmRequest("a.b", "khongcoso", "khongcoso"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("Nhap lai mat khau khong khop: 400 truoc khi cham toi token")
    void confirmWithMismatchedConfirmation() throws Exception {
        mockMvc.perform(confirmRequest("a.b", NEW_PASSWORD, "Khac@12345"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("Ba endpoint deu goi duoc khi chua dang nhap")
    void endpointsArePublic() throws Exception {
        mockMvc.perform(sendRequest("email", email)).andExpect(status().isOk());
        mockMvc.perform(verifyRequest(email, "123456")).andExpect(status().isBadRequest());
        mockMvc.perform(confirmRequest("a.b", NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isBadRequest());
    }

    // --- Tien ich ----------------------------------------------------------

    private String requestCodeFor(String address) throws Exception {
        if (userRepository.findByEmailIgnoreCase(address).isEmpty()) {
            persistUser(address, UserStatus.ACTIVE);
        }
        mockMvc.perform(sendRequest("email", address)).andExpect(status().isOk());
        return capturedCodes.latestFor(address).orElseThrow(
                () -> new IllegalStateException("Khong bat duoc ma xac minh"));
    }

    private String verifiedTokenFor(String address) throws Exception {
        String code = requestCodeFor(address);
        String body = mockMvc.perform(verifyRequest(address, code))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return node.get("resetToken").asText();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            sendRequest(String channel, String destination) {
        return post(SEND).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"channel":"%s","destination":"%s"}
                        """.formatted(channel, destination));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            verifyRequest(String destination, String code) {
        return post(VERIFY).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"destination":"%s","code":"%s"}
                        """.formatted(destination, code));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            confirmRequest(String token, String password, String confirmPassword) {
        return post(CONFIRM).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"resetToken":"%s","password":"%s","confirmPassword":"%s"}
                        """.formatted(token, password, confirmPassword));
    }

    private static String loginBody(String emailOrPhone, String password) {
        return """
                {"emailOrPhone":"%s","password":"%s"}
                """.formatted(emailOrPhone, password);
    }

    private User persistUser(String address, UserStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nguoi Dung Kiem Thu");
        user.setEmail(address);
        user.setPasswordHash(passwordEncoder.encode(OLD_PASSWORD));
        user.setStatus(status);
        return userRepository.save(user);
    }
}
