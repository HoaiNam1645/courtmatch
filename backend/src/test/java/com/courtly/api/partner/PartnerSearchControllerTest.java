package com.courtly.api.partner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.PlayType;
import com.courtly.common.enums.PlayingStyle;
import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.PlayerAvailabilitySlot;
import com.courtly.domain.account.PlayerAvailabilitySlotRepository;
import com.courtly.domain.account.PlayerPreferredLocation;
import com.courtly.domain.account.PlayerPreferredLocationRepository;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PartnerSearchControllerTest {

    private static final String PASSWORD = "Courtly@123";
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PlayerPreferredLocationRepository locationRepository;
    @Autowired private PlayerAvailabilitySlotRepository availabilityRepository;
    @Autowired private PlayerStatisticsRepository statisticsRepository;
    @Autowired private EntityManager entityManager;

    private User requester;
    private String token;
    private PlayerPreferredLocation requesterLocation;
    private ZonedDateTime requestedTime;

    @BeforeEach
    void setUp() throws Exception {
        ensurePlayerRole();
        requester = persistPlayer("requester", "1200.00", 12);
        token = loginAs(requester);
        requesterLocation = persistLocation(requester, 16.0609, 108.2237, 10);
        requestedTime = ZonedDateTime.now(VIETNAM_ZONE).plusDays(2)
                .withHour(18).withMinute(0).withSecond(0).withNano(0);
        // Nap lai entity de cot geography do trigger PostgreSQL sinh ra co mat trong test.
        entityManager.clear();
    }

    @Test
    @DisplayName("2.2.20-2.2.27 loc ung vien va tinh day du cac diem dac trung")
    void searchesEligiblePartnersAndReturnsBackendScores() throws Exception {
        User eligible = persistPlayer("eligible", "1280.00", 8);
        persistLocation(eligible, 16.0650, 108.2250, 10);
        persistAvailability(eligible, requestedTime.getDayOfWeek().getValue(), 17, 21);

        User tooFar = persistPlayer("far", "1210.00", 8);
        persistLocation(tooFar, 16.2000, 108.3500, 10);
        persistAvailability(tooFar, requestedTime.getDayOfWeek().getValue(), 17, 21);

        User unavailable = persistPlayer("unavailable", "1220.00", 8);
        persistLocation(unavailable, 16.0640, 108.2240, 10);
        persistAvailability(unavailable, requestedTime.getDayOfWeek().getValue(), 8, 10);

        User skillGap = persistPlayer("skill-gap", "1550.00", 8);
        persistLocation(skillGap, 16.0630, 108.2240, 10);
        persistAvailability(skillGap, requestedTime.getDayOfWeek().getValue(), 17, 21);

        entityManager.clear();
        mockMvc.perform(get("/api/v1/partners/search")
                        .header("Authorization", bearer(token))
                        .param("preferredLocationId", requesterLocation.getId().toString())
                        .param("scheduledAt", requestedTime.toInstant().toString())
                        .param("durationMinutes", "90")
                        .param("maxRatingDifference", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(eligible.getId().toString()))
                .andExpect(jsonPath("$.content[0].ratingScore").value(1280.00))
                .andExpect(jsonPath("$.content[0].ratingGap").value(80.00))
                .andExpect(jsonPath("$.content[0].skillSimilarity").value(77.37))
                .andExpect(jsonPath("$.content[0].distanceKm").isNumber())
                .andExpect(jsonPath("$.content[0].locationCompatibility").isNumber())
                .andExpect(jsonPath("$.content[0].timeCompatibility").value(100.00))
                .andExpect(jsonPath("$.content[0].styleCompatibility").value(100.00))
                .andExpect(jsonPath("$.content[0].availableFrom").value("17:00:00"))
                .andExpect(jsonPath("$.content[0].availableUntil").value("21:00:00"));
    }

    @Test
    @DisplayName("2.2.20 phan trang va khong yeu cau dang nhap bi tu choi")
    void validatesAuthenticationAndPagination() throws Exception {
        mockMvc.perform(get("/api/v1/partners/search")
                        .param("preferredLocationId", requesterLocation.getId().toString())
                        .param("scheduledAt", requestedTime.toInstant().toString())
                        .param("durationMinutes", "90"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/partners/search")
                        .header("Authorization", bearer(token))
                        .param("preferredLocationId", requesterLocation.getId().toString())
                        .param("scheduledAt", requestedTime.toInstant().toString())
                        .param("durationMinutes", "90")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("2.2.21 khu vuc khong thuoc nguoi dung hoac chua co toa do bi tu choi")
    void rejectsForeignAndUnpinnedLocations() throws Exception {
        User other = persistPlayer("location-owner", "1200.00", 1);
        PlayerPreferredLocation foreign = persistLocation(other, 16.06, 108.22, 5);
        PlayerPreferredLocation unpinned = persistUnpinnedLocation(requester);
        entityManager.clear();

        mockMvc.perform(get("/api/v1/partners/search")
                        .header("Authorization", bearer(token))
                        .param("preferredLocationId", foreign.getId().toString())
                        .param("scheduledAt", requestedTime.toInstant().toString())
                        .param("durationMinutes", "90"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/partners/search")
                        .header("Authorization", bearer(token))
                        .param("preferredLocationId", unpinned.getId().toString())
                        .param("scheduledAt", requestedTime.toInstant().toString())
                        .param("durationMinutes", "90"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARTNER_LOCATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors.preferredLocationId").isNotEmpty());
    }

    @Test
    @DisplayName("2.2.22 validate thoi gian qua khu, sai buoc va keo dai qua ngay")
    void validatesSearchTimeAndDuration() throws Exception {
        mockMvc.perform(get("/api/v1/partners/search")
                        .header("Authorization", bearer(token))
                        .param("preferredLocationId", requesterLocation.getId().toString())
                        .param("scheduledAt", Instant.now().minusSeconds(60).toString())
                        .param("durationMinutes", "90"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/partners/search")
                        .header("Authorization", bearer(token))
                        .param("preferredLocationId", requesterLocation.getId().toString())
                        .param("scheduledAt", requestedTime.toInstant().toString())
                        .param("durationMinutes", "45"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").isNotEmpty());

        ZonedDateTime late = requestedTime.withHour(23);
        mockMvc.perform(get("/api/v1/partners/search")
                        .header("Authorization", bearer(token))
                        .param("preferredLocationId", requesterLocation.getId().toString())
                        .param("scheduledAt", late.toInstant().toString())
                        .param("durationMinutes", "120"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.scheduledAt").isNotEmpty());
    }

    private User persistPlayer(String prefix, String rating, int totalMatches) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nguoi Choi " + prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.getUserRoles().add(new UserRole(
                user, roleRepository.findByCode(Role.PLAYER).orElseThrow()));
        PlayerProfile profile = new PlayerProfile(user);
        profile.setPlayingStyle(PlayingStyle.BALANCED);
        profile.setPreferredPlayType(PlayType.DOUBLE);
        user.setPlayerProfile(profile);
        userRepository.saveAndFlush(user);

        PlayerStatistics statistics = new PlayerStatistics(user);
        statistics.setRatingScore(new BigDecimal(rating));
        statistics.setTotalMatches(totalMatches);
        statisticsRepository.saveAndFlush(statistics);
        return user;
    }

    private PlayerPreferredLocation persistLocation(User user, double lat, double lng, double radius) {
        PlayerPreferredLocation location = new PlayerPreferredLocation();
        location.setId(UUID.randomUUID());
        location.setUser(user);
        location.setLabel("Khu vuc test");
        location.setLatitude(BigDecimal.valueOf(lat));
        location.setLongitude(BigDecimal.valueOf(lng));
        location.setRadiusKm(BigDecimal.valueOf(radius));
        return locationRepository.saveAndFlush(location);
    }

    private PlayerPreferredLocation persistUnpinnedLocation(User user) {
        PlayerPreferredLocation location = new PlayerPreferredLocation();
        location.setId(UUID.randomUUID());
        location.setUser(user);
        location.setLabel("Chua ghim");
        location.setRadiusKm(BigDecimal.valueOf(5));
        return locationRepository.saveAndFlush(location);
    }

    private void persistAvailability(User user, int day, int startHour, int endHour) {
        PlayerAvailabilitySlot slot = new PlayerAvailabilitySlot();
        slot.setId(UUID.randomUUID());
        slot.setUser(user);
        slot.setDayOfWeek((short) day);
        slot.setStartTime(LocalTime.of(startHour, 0));
        slot.setEndTime(LocalTime.of(endHour, 0));
        availabilityRepository.saveAndFlush(slot);
    }

    private void ensurePlayerRole() {
        if (roleRepository.findByCode(Role.PLAYER).isEmpty()) {
            Role role = new Role(Role.PLAYER, "Nguoi choi", null);
            role.setId(UUID.randomUUID());
            roleRepository.saveAndFlush(role);
        }
    }

    private String loginAs(User user) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"emailOrPhone":"%s","password":"%s"}
                                """.formatted(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
