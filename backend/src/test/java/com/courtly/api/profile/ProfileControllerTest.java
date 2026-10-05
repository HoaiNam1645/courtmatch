package com.courtly.api.profile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
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
 * Kiem thu 2.1.6 xem ho so, 2.1.7 chinh sua ho so,
 * 2.1.8 / 2.1.9 / 2.1.10 / 2.1.11 / 2.1.12 thiet lap choi.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileControllerTest {

    private static final String PROFILE = "/api/v1/users/me/profile";
    private static final String PREFERENCES = "/api/v1/users/me/preferences";
    private static final String PASSWORD = "Courtly@123";

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

    private String token;
    private String email;

    @BeforeEach
    void signIn() throws Exception {
        if (roleRepository.findByCode(Role.PLAYER).isEmpty()) {
            Role role = new Role(Role.PLAYER, "Nguoi choi", null);
            role.setId(UUID.randomUUID());
            roleRepository.save(role);
        }

        email = "profile-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nguoi Dung Kiem Thu");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.setPlayerProfile(new PlayerProfile(user));
        userRepository.save(user);

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailOrPhone":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(response).get("accessToken").asText();
    }

    // --- 2.1.6 Xem ho so --------------------------------------------------

    @Test
    @DisplayName("GET tra du nam khoi du lieu, thong ke la 0 khi chua choi tran nao")
    void getProfileReturnsAllSections() throws Exception {
        mockMvc.perform(get(PROFILE).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.profile").exists())
                .andExpect(jsonPath("$.statistics.totalMatches").value(0))
                .andExpect(jsonPath("$.availability").isArray())
                .andExpect(jsonPath("$.preferredLocations").isArray());
    }

    @Test
    @DisplayName("GET khong co token tra 401")
    void getProfileRequiresToken() throws Exception {
        mockMvc.perform(get(PROFILE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // --- 2.1.7 Chinh sua ho so --------------------------------------------

    @Test
    @DisplayName("Cap nhat ho so hop le tra 200 va du lieu moi")
    void updateProfileSuccess() throws Exception {
        String body = """
                {"fullName":"Ten Da Doi","email":"%s","gender":"female",
                 "dateOfBirth":"1996-05-20","bio":"Choi phong trao buoi toi."}
                """.formatted(email);

        mockMvc.perform(put(PROFILE).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.fullName").value("Ten Da Doi"))
                .andExpect(jsonPath("$.profile.gender").value("female"))
                .andExpect(jsonPath("$.profile.bio").value("Choi phong trao buoi toi."));
    }

    @Test
    @DisplayName("Ho ten qua ngan tra 400 kem loi theo field")
    void updateProfileRejectsShortName() throws Exception {
        mockMvc.perform(put(PROFILE).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"A","email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.fullName").isNotEmpty());
    }

    @Test
    @DisplayName("Ngay sinh o tuong lai tra 400")
    void updateProfileRejectsFutureBirthday() throws Exception {
        mockMvc.perform(put(PROFILE).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Nguoi Dung","email":"%s","dateOfBirth":"2090-01-01"}
                                """.formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dateOfBirth").isNotEmpty());
    }

    @Test
    @DisplayName("Nguoi choi duoi 10 tuoi tra 400")
    void updateProfileRejectsTooYoung() throws Exception {
        mockMvc.perform(put(PROFILE).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Nguoi Dung","email":"%s","dateOfBirth":"2023-01-01"}
                                """.formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.ageReasonable").isNotEmpty());
    }

    @Test
    @DisplayName("Bo trong ca email lan so dien thoai tra 400")
    void updateProfileRejectsMissingIdentity() throws Exception {
        mockMvc.perform(put(PROFILE).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Nguoi Dung"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.identityProvided").isNotEmpty());
    }

    @Test
    @DisplayName("Dung email cua tai khoan khac tra 409")
    void updateProfileRejectsEmailOfAnotherAccount() throws Exception {
        String otherEmail = "other-" + UUID.randomUUID() + "@example.com";
        User other = new User();
        other.setId(UUID.randomUUID());
        other.setFullName("Nguoi Khac");
        other.setEmail(otherEmail);
        other.setPasswordHash(passwordEncoder.encode(PASSWORD));
        other.setStatus(UserStatus.ACTIVE);
        userRepository.save(other);

        mockMvc.perform(put(PROFILE).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Nguoi Dung","email":"%s"}
                                """.formatted(otherEmail)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("Giu nguyen email cua chinh minh thi khong bao trung")
    void updateProfileAllowsKeepingOwnEmail() throws Exception {
        mockMvc.perform(put(PROFILE).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Van La Toi","email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    // --- 2.1.8 / 2.1.9 / 2.1.12 Thiet lap choi ----------------------------

    @Test
    @DisplayName("Luu thiet lap choi hop le tra 200 va ghi du ca ba nhom")
    void updatePreferencesSuccess() throws Exception {
        mockMvc.perform(put(PREFERENCES).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skillLevel":"advanced","dominantHand":"left",
                                 "playingStyle":"defensive","preferredPlayType":"mixed",
                                 "availability":[{"dayOfWeek":2,"startTime":"18:00","endTime":"21:00"}],
                                 "preferredLocations":[{"label":"Gan nha","address":"Da Nang","radiusKm":8,"isDefault":true}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.skillLevel").value("advanced"))
                .andExpect(jsonPath("$.profile.playingStyle").value("defensive"))
                .andExpect(jsonPath("$.profile.preferredPlayType").value("mixed"))
                .andExpect(jsonPath("$.availability.length()").value(1))
                .andExpect(jsonPath("$.preferredLocations[0].label").value("Gan nha"));
    }

    @Test
    @DisplayName("Luu lan hai ghi de toan bo danh sach cu, khong cong don")
    void updatePreferencesReplacesLists() throws Exception {
        mockMvc.perform(put(PREFERENCES).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"availability":[{"dayOfWeek":2,"startTime":"18:00","endTime":"21:00"},
                                                 {"dayOfWeek":4,"startTime":"18:00","endTime":"21:00"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability.length()").value(2));

        mockMvc.perform(put(PREFERENCES).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"availability":[{"dayOfWeek":6,"startTime":"07:00","endTime":"11:00"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availability.length()").value(1))
                .andExpect(jsonPath("$.availability[0].dayOfWeek").value(6));
    }

    @Test
    @DisplayName("playingStyle ngoai danh sach tra 400 va chi dung ten field")
    void updatePreferencesRejectsUnknownPlayingStyle() throws Exception {
        mockMvc.perform(put(PREFERENCES).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"playingStyle":"all_round"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.playingStyle").isNotEmpty());
    }

    // --- 2.1.10 Khung gio ranh --------------------------------------------

    @Test
    @DisplayName("Trung thu trong tuan tra 400")
    void updatePreferencesRejectsDuplicateDay() throws Exception {
        mockMvc.perform(put(PREFERENCES).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"availability":[{"dayOfWeek":2,"startTime":"18:00","endTime":"20:00"},
                                                 {"dayOfWeek":2,"startTime":"08:00","endTime":"10:00"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dayOfWeekUnique").isNotEmpty());
    }

    @Test
    @DisplayName("Gio ket thuc truoc gio bat dau tra 400")
    void updatePreferencesRejectsInvertedTimeRange() throws Exception {
        mockMvc.perform(put(PREFERENCES).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"availability":[{"dayOfWeek":3,"startTime":"21:00","endTime":"18:00"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['availability[0].timeRangeValid']").isNotEmpty());
    }

    @Test
    @DisplayName("Thu trong tuan ngoai 1-7 tra 400")
    void updatePreferencesRejectsDayOutOfRange() throws Exception {
        mockMvc.perform(put(PREFERENCES).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"availability":[{"dayOfWeek":9,"startTime":"18:00","endTime":"20:00"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['availability[0].dayOfWeek']").isNotEmpty());
    }

    // --- 2.1.11 Dia diem uu tien ------------------------------------------

    @Test
    @DisplayName("Hai dia diem cung mac dinh tra 400")
    void updatePreferencesRejectsTwoDefaultLocations() throws Exception {
        mockMvc.perform(put(PREFERENCES).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredLocations":[{"label":"A","radiusKm":5,"isDefault":true},
                                                       {"label":"B","radiusKm":5,"isDefault":true}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.defaultLocationUnique").isNotEmpty());
    }

    @Test
    @DisplayName("Ban kinh vuot 30 km tra 400, khop gioi han thanh truot cua giao dien")
    void updatePreferencesRejectsRadiusAboveSliderRange() throws Exception {
        mockMvc.perform(put(PREFERENCES).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredLocations":[{"label":"A","radiusKm":50,"isDefault":true}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['preferredLocations[0].radiusKm']").isNotEmpty());
    }

    @Test
    @DisplayName("Ten dia diem de trong tra 400")
    void updatePreferencesRejectsBlankLocationLabel() throws Exception {
        mockMvc.perform(put(PREFERENCES).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredLocations":[{"label":"  ","radiusKm":5,"isDefault":true}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['preferredLocations[0].label']").isNotEmpty());
    }

    @Test
    @DisplayName("PUT /preferences khong co token tra 401")
    void updatePreferencesRequiresToken() throws Exception {
        mockMvc.perform(put(PREFERENCES).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private String bearer() {
        return "Bearer " + token;
    }
}
