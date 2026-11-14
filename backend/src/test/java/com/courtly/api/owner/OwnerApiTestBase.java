package com.courtly.api.owner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.CourtStatus;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.common.enums.VerificationStatus;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtPriceRule;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueOperatingHours;
import com.courtly.domain.venue.VenueRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import com.courtly.domain.account.CourtOwnerProfile;
import com.courtly.domain.account.CourtOwnerProfileRepository;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Du lieu va thao tac dung chung cho test cac API cua chu san (2.3.x). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class OwnerApiTestBase {

    protected static final String PASSWORD = "Courtly@123";
    protected static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired protected VenueRepository venueRepository;
    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected CourtOwnerProfileRepository ownerProfileRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    protected void ensureRoles() {
        ensureRole(Role.PLAYER, "Nguoi choi");
        ensureRole(Role.OWNER, "Chu san");
    }

    protected User persistOwner(String prefix) {
        User owner = persistUser(prefix, Role.OWNER);
        CourtOwnerProfile profile = new CourtOwnerProfile(owner);
        profile.setBusinessName("Ho kinh doanh " + prefix);
        profile.setBankName("MB Bank");
        profile.setBankAccountNo("0359111001");
        profile.setBankAccountName("NGUYEN HUU THANG");
        profile.setVerificationStatus(VerificationStatus.VERIFIED);
        ownerProfileRepository.saveAndFlush(profile);
        return owner;
    }

    protected User persistUser(String prefix, String roleCode) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nguoi Dung " + prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.getUserRoles().add(new UserRole(user, roleRepository.findByCode(roleCode).orElseThrow()));
        if (Role.PLAYER.equals(roleCode)) {
            user.setPlayerProfile(new PlayerProfile(user));
        }
        return userRepository.saveAndFlush(user);
    }

    protected String loginAs(User user) throws Exception {
        return loginAs(user.getEmail(), PASSWORD);
    }

    protected String loginAs(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailOrPhone":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    /**
     * San da duyet, mo cua 06:00-22:00 moi ngay, mot san con "A" gia 120.000d/gio ca ngay.
     */
    protected Venue persistVenue(User owner, String name) {
        Venue venue = new Venue();
        venue.setId(UUID.randomUUID());
        venue.setOwner(owner);
        venue.setName(name);
        venue.setSlug("kt-owner-" + UUID.randomUUID());
        venue.setAddress("12 Bach Dang");
        venue.setDistrict("Hải Châu");
        venue.setProvince("Da Nang");
        venue.setPhone("02363888999");
        venue.setStatus(VenueStatus.ACTIVE);
        venue.setApprovalStatus(ApprovalStatus.APPROVED);
        venue.setLatitude(BigDecimal.valueOf(16.0609));
        venue.setLongitude(BigDecimal.valueOf(108.2237));

        for (int day = 1; day <= 7; day++) {
            VenueOperatingHours hours = new VenueOperatingHours();
            hours.setId(UUID.randomUUID());
            hours.setVenue(venue);
            hours.setDayOfWeek((short) day);
            hours.setOpenTime(LocalTime.of(6, 0));
            hours.setCloseTime(LocalTime.of(22, 0));
            venue.getOperatingHours().add(hours);
        }

        Court court = new Court();
        court.setId(UUID.randomUUID());
        court.setVenue(venue);
        court.setName("San A");
        court.setCourtCode("A");
        court.setStatus(CourtStatus.ACTIVE);

        CourtPriceRule rule = new CourtPriceRule();
        rule.setId(UUID.randomUUID());
        rule.setCourt(court);
        rule.setStartTime(LocalTime.of(6, 0));
        rule.setEndTime(LocalTime.of(22, 0));
        rule.setPricePerHour(BigDecimal.valueOf(120_000));
        rule.setStatus(ActiveStatus.ACTIVE);
        rule.setEffectiveFrom(LocalDate.now(ZONE).minusYears(1));
        court.getPriceRules().add(rule);
        venue.getCourts().add(court);
        return venueRepository.saveAndFlush(venue);
    }

    /** Ngay thu Hai gan nhat sau hom nay, de lich mo cua va gia luon xac dinh. */
    protected static LocalDate nextMonday() {
        LocalDate date = LocalDate.now(ZONE).plusDays(1);
        while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    /** Nguoi choi tao yeu cau dat san 90 phut tai san con cho truoc; tra ve bookingId. */
    protected String createBooking(String playerToken, Court court, LocalDate date, LocalTime start) throws Exception {
        String response = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courtId":"%s","startTime":"%s","durationMinutes":90,"note":null}
                                """.formatted(court.getId(), date.atTime(start).atZone(ZONE).toInstant())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    protected void ensureRole(String code, String name) {
        if (roleRepository.findByCode(code).isEmpty()) {
            Role role = new Role(code, name, null);
            role.setId(UUID.randomUUID());
            roleRepository.save(role);
        }
    }
}
