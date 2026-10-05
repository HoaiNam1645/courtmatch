package com.courtly.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.config.EmailVerificationProperties;
import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.EmailVerificationRequest;
import com.courtly.domain.account.EmailVerificationRequestRepository;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.service.auth.OtpRateLimiter;
import com.courtly.service.mail.EmailVerificationCodeIssued;
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
 * Kiem thu xac minh email khi dang ky (2.1.1).
 *
 * <p>Ma xac minh khong ra khoi he thong qua API nen test bat su kien
 * {@link EmailVerificationCodeIssued} - dung cai ma MailService nhan.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailVerificationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String VERIFY = "/api/v1/auth/verify-email";
    private static final String RESEND = "/api/v1/auth/verify-email/resend";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String PASSWORD = "Courtly@123";

    @TestConfiguration
    static class CapturedCodesConfig {
        @Bean
        CapturedCodes capturedVerificationCodes() {
            return new CapturedCodes();
        }
    }

    /** Nghe su kien ngay khi phat (khong cho commit) de test lay duoc ma goc. */
    static class CapturedCodes {
        private final List<EmailVerificationCodeIssued> events = new ArrayList<>();

        @EventListener
        void on(EmailVerificationCodeIssued event) {
            events.add(event);
        }

        void clear() {
            events.clear();
        }

        Optional<String> latestFor(String email) {
            return events.stream()
                    .filter(event -> event.email().equals(email))
                    .reduce((first, second) -> second)
                    .map(EmailVerificationCodeIssued::code);
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
    private RoleRepository roleRepository;

    @Autowired
    private EmailVerificationRequestRepository verificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OtpRateLimiter rateLimiter;

    @Autowired
    private EmailVerificationProperties properties;

    @Autowired
    private CapturedCodes capturedCodes;

    @MockitoBean
    private com.courtly.service.mail.MailService mailService;

    private String email;

    @BeforeEach
    void setUp() {
        if (roleRepository.findByCode(Role.PLAYER).isEmpty()) {
            Role role = new Role(Role.PLAYER, "Nguoi choi", null);
            role.setId(UUID.randomUUID());
            roleRepository.save(role);
        }
        capturedCodes.clear();
        email = "verify-" + UUID.randomUUID() + "@example.com";
        rateLimiter.reset("verify", email);
    }

    // --- Dang ky phat ma ---------------------------------------------------

    @Test
    @DisplayName("Dang ky xong sinh ma 6 chu so va mot dong cho xac minh")
    void registerIssuesCode() throws Exception {
        register(email);

        assertThat(capturedCodes.latestFor(email)).hasValueSatisfying(
                code -> assertThat(code).matches("\\d{6}"));
        assertThat(verificationRepository.findLatestPending(email)).isPresent();
    }

    @Test
    @DisplayName("Database chi luu hash, khong luu ma goc")
    void codeIsStoredAsHashOnly() throws Exception {
        register(email);
        String code = capturedCodes.latestFor(email).orElseThrow();

        EmailVerificationRequest saved = verificationRepository.findLatestPending(email).orElseThrow();
        assertThat(saved.getVerificationHash()).doesNotContain(code);
        assertThat(passwordEncoder.matches(code, saved.getVerificationHash())).isTrue();
    }

    @Test
    @DisplayName("Tai khoan chua xac minh thi khong dang nhap duoc")
    void pendingAccountCannotLogIn() throws Exception {
        register(email);

        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginBody(email)))
                .andExpect(status().isForbidden())
                // Ma loi rieng de frontend dua nguoi dung sang man hinh nhap ma,
                // khong lan voi tai khoan bi khoa.
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    // --- Nhap ma -----------------------------------------------------------

    @Test
    @DisplayName("Ma dung: kich hoat tai khoan, ghi email_verified_at va cap token")
    void verifyActivatesAccount() throws Exception {
        register(email);
        String code = capturedCodes.latestFor(email).orElseThrow();

        mockMvc.perform(verifyRequest(email, code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.status").value("active"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmailVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("Xac minh xong thi dang nhap duoc")
    void loginWorksAfterVerification() throws Exception {
        register(email);
        mockMvc.perform(verifyRequest(email, capturedCodes.latestFor(email).orElseThrow()))
                .andExpect(status().isOk());

        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginBody(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("Ma sai: 400 va so lan thu duoc ghi lai, khong bi rollback")
    void wrongCodeCountsAttempt() throws Exception {
        register(email);

        mockMvc.perform(verifyRequest(email, "000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));

        assertThat(verificationRepository.findLatestPending(email).orElseThrow().getAttemptCount())
                .isEqualTo((short) 1);
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING);
    }

    @Test
    @DisplayName("Sai qua so lan cho phep: 429 va ma bi huy hoan toan")
    void tooManyAttemptsKillsTheCode() throws Exception {
        register(email);
        String code = capturedCodes.latestFor(email).orElseThrow();

        for (int attempt = 1; attempt < properties.maxAttempts(); attempt++) {
            mockMvc.perform(verifyRequest(email, "000000")).andExpect(status().isBadRequest());
        }
        mockMvc.perform(verifyRequest(email, "000000"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFICATION_TOO_MANY_ATTEMPTS"));

        // Ma that cung khong con dung duoc nua.
        mockMvc.perform(verifyRequest(email, code)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Ma het han: 400 VERIFICATION_CODE_INVALID")
    void expiredCodeIsRejected() throws Exception {
        register(email);
        String code = capturedCodes.latestFor(email).orElseThrow();
        EmailVerificationRequest saved = verificationRepository.findLatestPending(email).orElseThrow();
        saved.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        verificationRepository.saveAndFlush(saved);

        mockMvc.perform(verifyRequest(email, code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
    }

    @Test
    @DisplayName("Dung lai ma da xac minh: 400, mot ma chi dung duoc mot lan")
    void codeCannotBeReused() throws Exception {
        register(email);
        String code = capturedCodes.latestFor(email).orElseThrow();
        mockMvc.perform(verifyRequest(email, code)).andExpect(status().isOk());

        mockMvc.perform(verifyRequest(email, code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
    }

    @Test
    @DisplayName("Chua tung dang ky: 400 giong het truong hop nhap sai ma")
    void verifyUnknownEmail() throws Exception {
        mockMvc.perform(verifyRequest(email, "123456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_INVALID"));
    }

    @Test
    @DisplayName("Ma khong phai 6 chu so: 400 VALIDATION_FAILED")
    void malformedCodeIsRejected() throws Exception {
        mockMvc.perform(verifyRequest(email, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.code").exists());
    }

    // --- Gui lai ma --------------------------------------------------------

    @Test
    @DisplayName("Gui lai ma: ma cu het hieu luc, chi ma moi dung duoc")
    void resendInvalidatesPreviousCode() throws Exception {
        register(email);
        String firstCode = capturedCodes.latestFor(email).orElseThrow();

        rateLimiter.reset("verify", email);
        mockMvc.perform(resendRequest(email)).andExpect(status().isOk());
        String secondCode = capturedCodes.latestFor(email).orElseThrow();

        mockMvc.perform(verifyRequest(email, firstCode)).andExpect(status().isBadRequest());
        mockMvc.perform(verifyRequest(email, secondCode)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Bam gui lai ngay: 429 kem so giay con phai doi")
    void resendTooSoon() throws Exception {
        register(email);
        rateLimiter.reset("verify", email);

        mockMvc.perform(resendRequest(email)).andExpect(status().isOk());
        mockMvc.perform(resendRequest(email))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.fieldErrors.retryAfterSeconds").exists());
    }

    @Test
    @DisplayName("Gui lai cho email khong ton tai: van tra 200, khong lo email nao co that")
    void resendUnknownEmailLooksIdentical() throws Exception {
        mockMvc.perform(resendRequest(email))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        assertThat(capturedCodes.countFor(email)).isZero();
    }

    @Test
    @DisplayName("Gui lai cho tai khoan da xac minh: khong phat them ma")
    void resendForVerifiedAccountDoesNothing() throws Exception {
        register(email);
        mockMvc.perform(verifyRequest(email, capturedCodes.latestFor(email).orElseThrow()))
                .andExpect(status().isOk());

        capturedCodes.clear();
        rateLimiter.reset("verify", email);
        mockMvc.perform(resendRequest(email)).andExpect(status().isOk());

        assertThat(capturedCodes.countFor(email)).isZero();
    }

    @Test
    @DisplayName("Han muc gui lai cua xac minh tach khoi han muc dat lai mat khau")
    void resendBudgetIsSeparateFromPasswordReset() throws Exception {
        register(email);
        rateLimiter.reset("verify", email);
        mockMvc.perform(resendRequest(email)).andExpect(status().isOk());

        // Vua dung luot cua luong "verify"; luong "reset" phai con nguyen.
        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"email\",\"destination\":\"%s\"}".formatted(email)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ba endpoint deu goi duoc khi chua dang nhap")
    void endpointsArePublic() throws Exception {
        mockMvc.perform(verifyRequest(email, "123456")).andExpect(status().isBadRequest());
        mockMvc.perform(resendRequest(email)).andExpect(status().isOk());
    }

    // --- Tien ich ----------------------------------------------------------

    private void register(String address) throws Exception {
        String body = """
                {"fullName":"Nguoi Dung Moi","email":"%s","password":"%s","confirmPassword":"%s"}
                """.formatted(address, PASSWORD, PASSWORD);
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            verifyRequest(String address, String code) {
        return post(VERIFY).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","code":"%s"}
                        """.formatted(address, code));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            resendRequest(String address) {
        return post(RESEND).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s"}
                        """.formatted(address));
    }

    private static String loginBody(String address) {
        return """
                {"emailOrPhone":"%s","password":"%s"}
                """.formatted(address, PASSWORD);
    }
}
