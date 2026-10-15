package com.courtly.api.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.CourtStatus;
import com.courtly.common.enums.PaymentStatus;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.domain.account.CourtOwnerProfile;
import com.courtly.domain.account.CourtOwnerProfileRepository;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtPriceRule;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueOperatingHours;
import com.courtly.domain.venue.VenueRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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

/** Integration tests for direct player-to-owner payment confirmation. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentControllerTest {

    private static final String BOOKINGS = "/api/v1/bookings";
    private static final String PAYMENTS = "/api/v1/payments";
    private static final String PASSWORD = "Courtly@123";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CourtOwnerProfileRepository ownerProfileRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String playerToken;
    private String ownerToken;
    private Court court;
    private LocalDate futureDate;

    @BeforeEach
    void seed() throws Exception {
        ensureRole(Role.PLAYER, "Nguoi choi");
        ensureRole(Role.OWNER, "Chu san");

        futureDate = LocalDate.now(ZONE).plusDays(1);
        while (futureDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            futureDate = futureDate.plusDays(1);
        }

        User owner = persistUser("owner", Role.OWNER);
        CourtOwnerProfile ownerProfile = new CourtOwnerProfile(owner);
        ownerProfile.setBankName("MB Bank");
        ownerProfile.setBankAccountNo("0359111001");
        ownerProfile.setBankAccountName("NGUYEN HUU THANG");
        ownerProfileRepository.saveAndFlush(ownerProfile);

        User player = persistUser("player", Role.PLAYER);
        playerToken = loginAs(player);
        ownerToken = loginAs(owner);

        Venue venue = new Venue();
        venue.setId(UUID.randomUUID());
        venue.setOwner(owner);
        venue.setName("San Kiem Thu Thanh Toan");
        venue.setSlug("kt-tt-" + UUID.randomUUID());
        venue.setPhone("0900000000");
        venue.setAddress("34 Kiem Thu");
        venue.setStatus(VenueStatus.ACTIVE);
        venue.setApprovalStatus(ApprovalStatus.APPROVED);
        venue.setLatitude(BigDecimal.valueOf(16.05));
        venue.setLongitude(BigDecimal.valueOf(108.20));

        for (int day = 1; day <= 7; day++) {
            VenueOperatingHours hours = new VenueOperatingHours();
            hours.setId(UUID.randomUUID());
            hours.setVenue(venue);
            hours.setDayOfWeek((short) day);
            hours.setOpenTime(LocalTime.of(8, 0));
            hours.setCloseTime(LocalTime.of(22, 0));
            venue.getOperatingHours().add(hours);
        }

        court = new Court();
        court.setId(UUID.randomUUID());
        court.setVenue(venue);
        court.setName("San A");
        court.setCourtCode("A");
        court.setStatus(CourtStatus.ACTIVE);

        CourtPriceRule rule = new CourtPriceRule();
        rule.setId(UUID.randomUUID());
        rule.setCourt(court);
        rule.setStartTime(LocalTime.of(8, 0));
        rule.setEndTime(LocalTime.of(22, 0));
        rule.setPricePerHour(BigDecimal.valueOf(120_000));
        rule.setStatus(ActiveStatus.ACTIVE);
        rule.setEffectiveFrom(LocalDate.now(ZONE).minusYears(1));
        court.getPriceRules().add(rule);
        venue.getCourts().add(court);
        venueRepository.saveAndFlush(venue);
    }

    @Test
    @DisplayName("Tao booking chi tao yeu cau, chua tao payment")
    void createBookingWaitsForOwner() throws Exception {
        String bookingId = createBooking(LocalTime.of(9, 0));

        assertThat(bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_OWNER_CONFIRMATION);
        assertThat(paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(UUID.fromString(bookingId)))
                .isEmpty();
    }

    @Test
    @DisplayName("Owner chap nhan thi tao huong dan thanh toan bang tai khoan owner")
    void ownerAcceptCreatesDirectPayment() throws Exception {
        String bookingId = createAndAccept(LocalTime.of(10, 0));
        Payment payment = paymentOf(bookingId);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AWAITING_PAYMENT);
        assertThat(payment.getBankAccountNo()).isEqualTo("0359111001");
        assertThat(payment.getRecipientOwner().getId()).isNotNull();

        mockMvc.perform(get(PAYMENTS + "/by-booking/" + bookingId)
                        .header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("awaiting_payment"))
                .andExpect(jsonPath("$.bankAccountNo").value("0359111001"))
                .andExpect(jsonPath("$.referenceContent").isNotEmpty());
    }

    @Test
    @DisplayName("Nguoi choi bao da tra khong tu dong confirm booking")
    void playerReportDoesNotConfirmBooking() throws Exception {
        String bookingId = createAndAccept(LocalTime.of(11, 0));
        Payment payment = paymentOf(bookingId);

        mockMvc.perform(post(PAYMENTS + "/" + payment.getId() + "/report-paid")
                        .header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"method":"owner_bank_transfer","proofUrl":"https://example.com/proof.jpg"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("payer_reported"));

        assertThat(bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.AWAITING_PAYMENT);
    }

    @Test
    @DisplayName("Owner xac nhan da thu tien moi confirm booking")
    void ownerConfirmationConfirmsBooking() throws Exception {
        String bookingId = createAndAccept(LocalTime.of(12, 0));
        Payment payment = paymentOf(bookingId);
        reportPaid(payment);

        mockMvc.perform(post(PAYMENTS + "/" + payment.getId() + "/confirm-received")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("confirmed"));

        assertThat(bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Nguoi khac khong the xac nhan tien thay owner")
    void playerCannotConfirmForOwner() throws Exception {
        String bookingId = createAndAccept(LocalTime.of(13, 0));
        Payment payment = paymentOf(bookingId);
        reportPaid(payment);

        mockMvc.perform(post(PAYMENTS + "/" + payment.getId() + "/confirm-received")
                        .header("Authorization", bearer(playerToken)))
                .andExpect(status().isNotFound());
    }

    private void reportPaid(Payment payment) throws Exception {
        mockMvc.perform(post(PAYMENTS + "/" + payment.getId() + "/report-paid")
                        .header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"owner_bank_transfer\",\"proofUrl\":null}"))
                .andExpect(status().isOk());
    }

    private String createAndAccept(LocalTime start) throws Exception {
        String bookingId = createBooking(start);
        mockMvc.perform(post(BOOKINGS + "/" + bookingId + "/accept")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("awaiting_payment"));
        return bookingId;
    }

    private Payment paymentOf(String bookingId) {
        return paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(UUID.fromString(bookingId))
                .orElseThrow();
    }

    private String createBooking(LocalTime start) throws Exception {
        String response = mockMvc.perform(post(BOOKINGS)
                        .header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courtId":"%s","startTime":"%s","durationMinutes":90,"note":null}
                                """.formatted(court.getId(), at(start))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending_owner_confirmation"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String at(LocalTime time) {
        return futureDate.atTime(time).atZone(ZONE).toInstant().toString();
    }

    private void ensureRole(String code, String name) {
        if (roleRepository.findByCode(code).isEmpty()) {
            Role role = new Role(code, name, null);
            role.setId(UUID.randomUUID());
            roleRepository.save(role);
        }
    }

    private User persistUser(String prefix, String roleCode) {
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

    private String loginAs(User user) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
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
