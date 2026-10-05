package com.courtly.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiem thu 2.1.1 dang ky, 2.1.2 dang nhap, 2.1.4 dang xuat.
 *
 * <p>Chay tren database dev nhung moi test deu {@code @Transactional} nen duoc rollback,
 * khong de lai du lieu. Test tu tao du lieu can dung, khong phu thuoc seeder.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String VALID_PASSWORD = "Courtly@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void ensurePlayerRoleExists() {
        if (roleRepository.findByCode(Role.PLAYER).isEmpty()) {
            Role role = new Role(Role.PLAYER, "Nguoi choi", null);
            role.setId(UUID.randomUUID());
            roleRepository.save(role);
        }
    }

    // --- 2.1.1 Dang ky -----------------------------------------------------

    @Test
    @DisplayName("Dang ky hop le tra 201, KHONG kem token, tai khoan cho xac minh email")
    void registerSuccess() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, uniquePhone(), VALID_PASSWORD, VALID_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.expiresInSeconds").isNumber())
                .andExpect(jsonPath("$.resendAfterSeconds").isNumber())
                // Tai khoan chua xac minh thi chua duoc cap token.
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        User created = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(created.getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(created.getEmailVerifiedAt()).isNull();
    }

    @Test
    @DisplayName("Thieu ho ten tra 400 kem loi theo field")
    void registerRejectsBlankFullName() throws Exception {
        String body = """
                {"fullName":"  ","email":"%s","password":"%s","confirmPassword":"%s"}
                """.formatted(uniqueEmail(), VALID_PASSWORD, VALID_PASSWORD);

        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.fullName").isNotEmpty());
    }

    @Test
    @DisplayName("Mat khau nhap lai khong khop tra 400")
    void registerRejectsPasswordMismatch() throws Exception {
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), uniquePhone(), VALID_PASSWORD, "KhacHoanToan9")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.passwordConfirmed").isNotEmpty());
    }

    @Test
    @DisplayName("Mat khau khong co chu so tra 400")
    void registerRejectsWeakPassword() throws Exception {
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), uniquePhone(), "khongcochuso", "khongcochuso")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
    }

    @Test
    @DisplayName("Thieu email tra 400 - email bat buoc tu khi co xac minh email")
    void registerRejectsMissingEmail() throws Exception {
        String body = """
                {"fullName":"Khong Co Email","phone":"%s","password":"%s","confirmPassword":"%s"}
                """.formatted(uniquePhone(), VALID_PASSWORD, VALID_PASSWORD);

        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
    }

    @Test
    @DisplayName("Dang ky khong co so dien thoai van duoc - chi email la bat buoc")
    void registerWithoutPhoneIsAllowed() throws Exception {
        String body = """
                {"fullName":"Chi Co Email","email":"%s","password":"%s","confirmPassword":"%s"}
                """.formatted(uniqueEmail(), VALID_PASSWORD, VALID_PASSWORD);

        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("So dien thoai khong phai di dong Viet Nam tra 400")
    void registerRejectsNonVietnameseMobile() throws Exception {
        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), "0123456789", VALID_PASSWORD, VALID_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").isNotEmpty());
    }

    @Test
    @DisplayName("Email da ton tai tra 409")
    void registerRejectsDuplicateEmail() throws Exception {
        String email = uniqueEmail();
        persistUser(email, uniquePhone(), UserStatus.ACTIVE);

        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, uniquePhone(), VALID_PASSWORD, VALID_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("So dien thoai da ton tai tra 409")
    void registerRejectsDuplicatePhone() throws Exception {
        String phone = uniquePhone();
        persistUser(uniqueEmail(), phone, UserStatus.ACTIVE);

        mockMvc.perform(post(REGISTER).contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), phone, VALID_PASSWORD, VALID_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_EXISTS"));
    }

    // --- 2.1.2 Dang nhap ---------------------------------------------------

    @Test
    @DisplayName("Dang nhap bang email tra 200")
    void loginWithEmail() throws Exception {
        String email = uniqueEmail();
        persistUser(email, uniquePhone(), UserStatus.ACTIVE);

        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginBody(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("Dang nhap bang so dien thoai dang +84 van nhan dung tai khoan")
    void loginNormalizesInternationalPhone() throws Exception {
        String phone = uniquePhone();
        persistUser(uniqueEmail(), phone, UserStatus.ACTIVE);

        String international = "+84 " + phone.substring(1);
        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginBody(international)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.phone").value(phone));
    }

    @Test
    @DisplayName("Sai mat khau va tai khoan khong ton tai tra cung mot thong bao")
    void loginDoesNotRevealAccountExistence() throws Exception {
        String email = uniqueEmail();
        persistUser(email, uniquePhone(), UserStatus.ACTIVE);

        String wrongPassword = mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailOrPhone":"%s","password":"SaiMatKhau9"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        String unknownAccount = mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(uniqueEmail())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        // Hai thong bao phai giong nhau, neu khac la lo tai khoan nao ton tai.
        org.assertj.core.api.Assertions
                .assertThat(readMessage(wrongPassword))
                .isEqualTo(readMessage(unknownAccount));
    }

    @Test
    @DisplayName("Tai khoan chi dang nhap bang Google (khong co mat khau) tra 401")
    void loginRejectsAccountWithoutPassword() throws Exception {
        String email = uniqueEmail();
        User user = persistUser(email, uniquePhone(), UserStatus.ACTIVE);
        user.setPasswordHash(null);
        userRepository.save(user);

        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginBody(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("Tai khoan bi khoa tra 403, khong phai 401")
    void loginRejectsBannedAccount() throws Exception {
        String email = uniqueEmail();
        persistUser(email, uniquePhone(), UserStatus.BANNED);

        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(loginBody(email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("Thieu mat khau tra 400")
    void loginRejectsMissingPassword() throws Exception {
        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailOrPhone":"ai.do@example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").isNotEmpty());
    }

    @Test
    @DisplayName("JSON sai cu phap tra 400 va khong lo stacktrace")
    void loginRejectsMalformedJson() throws Exception {
        mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON).content("{\"emailOrPhone\": }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    // --- Endpoint can xac thuc --------------------------------------------

    @Test
    @DisplayName("GET /me kem token tra dung tai khoan dang dang nhap")
    void meReturnsCurrentUser() throws Exception {
        String email = uniqueEmail();
        persistUser(email, uniquePhone(), UserStatus.ACTIVE);

        String response = mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email)))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("GET /me khong co token tra 401 dung hinh dang ApiError")
    void meRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /me voi token gia mao tra 401")
    void meRejectsForgedToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.giamao"))
                .andExpect(status().isUnauthorized());
    }

    // --- 2.1.4 Dang xuat ---------------------------------------------------

    @Test
    @DisplayName("POST /logout tra 204")
    void logoutReturnsNoContent() throws Exception {
        String email = uniqueEmail();
        persistUser(email, uniquePhone(), UserStatus.ACTIVE);

        String response = mockMvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email)))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // --- Tien ich ----------------------------------------------------------

    private User persistUser(String email, String phone, UserStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nguoi Dung Kiem Thu");
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(VALID_PASSWORD));
        user.setStatus(status);
        return userRepository.save(user);
    }

    private String readMessage(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return node.get("message").asText();
    }

    private static String registerBody(String email, String phone, String password, String confirm) {
        return """
                {"fullName":"Nguoi Dung Moi","email":"%s","phone":"%s","password":"%s","confirmPassword":"%s"}
                """.formatted(email, phone, password, confirm);
    }

    private static String loginBody(String emailOrPhone) {
        return """
                {"emailOrPhone":"%s","password":"%s"}
                """.formatted(emailOrPhone, VALID_PASSWORD);
    }

    private static String uniqueEmail() {
        return "it-" + UUID.randomUUID() + "@example.com";
    }

    /** So di dong Viet Nam ngau nhien, tranh dung unique index khi chay lai. */
    private static String uniquePhone() {
        long suffix = Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L);
        return "09" + String.format("%08d", suffix);
    }
}
