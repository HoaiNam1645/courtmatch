package com.courtly.api.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.CourtStatus;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtPriceRule;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueOperatingHours;
import com.courtly.domain.venue.VenueRepository;
import com.courtly.service.booking.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
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

/**
 * Kiem thu 2.1.28 tao don, 2.1.29 chi tiet, 2.1.30 trang thai,
 * 2.1.31 huy don, 2.1.32 lich su.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingControllerTest {

    private static final String BOOKINGS = "/api/v1/bookings";
    private static final String PASSWORD = "Courtly@123";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String token;
    private Court court;
    private Venue venue;
    private LocalDate futureDate;

    @BeforeEach
    void seed() throws Exception {
        if (roleRepository.findByCode(Role.PLAYER).isEmpty()) {
            Role role = new Role(Role.PLAYER, "Nguoi choi", null);
            role.setId(UUID.randomUUID());
            roleRepository.save(role);
        }

        futureDate = LocalDate.now(ZONE).plusDays(1);
        while (futureDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            futureDate = futureDate.plusDays(1);
        }

        User owner = persistUser("owner");
        token = loginAs(persistUser("player"));

        venue = new Venue();
        venue.setId(UUID.randomUUID());
        venue.setOwner(owner);
        venue.setName("San Kiem Thu Dat");
        venue.setSlug("kt-dat-" + UUID.randomUUID());
        venue.setPhone("0900000000");
        venue.setAddress("12 Kiem Thu");
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

    // --- 2.1.28 Tao don ----------------------------------------------------

    @Test
    @DisplayName("Tao don hop le tra 201, gia do server tinh, trang thai cho thanh toan")
    void createBooking() throws Exception {
        mockMvc.perform(post(BOOKINGS).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(LocalTime.of(9, 0), 90, "Nho chuan bi cau")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending_payment"))
                // 120.000 x 1,5 gio
                .andExpect(jsonPath("$.totalAmount").value(180000))
                .andExpect(jsonPath("$.bookingCode").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.note").value("Nho chuan bi cau"))
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.statusHistory.length()").value(1));
    }

    @Test
    @DisplayName("Khong gui so tien - server tu tinh, khong nhan gia tu client")
    void ignoresClientSuppliedPrice() throws Exception {
        String body = """
                {"courtId":"%s","startTime":"%s","durationMinutes":60,"totalAmount":1}
                """.formatted(court.getId(), at(LocalTime.of(9, 0)));

        mockMvc.perform(post(BOOKINGS).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(120000));
    }

    @Test
    @DisplayName("Dat trung khung gio tra 409")
    void rejectsOverlappingBooking() throws Exception {
        mockMvc.perform(post(BOOKINGS).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(LocalTime.of(9, 0), 60, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(BOOKINGS).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(LocalTime.of(9, 0), 60, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_SLOT_TAKEN"));
    }

    @Test
    @DisplayName("Dat ngoai gio mo cua tra 409")
    void rejectsOutsideOpeningHours() throws Exception {
        mockMvc.perform(post(BOOKINGS).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(LocalTime.of(23, 0), 60, null)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Thoi luong 45 phut tra 400")
    void rejectsInvalidDuration() throws Exception {
        mockMvc.perform(post(BOOKINGS).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(LocalTime.of(9, 0), 45, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").isNotEmpty());
    }

    @Test
    @DisplayName("Chua dang nhap thi khong tao duoc don")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post(BOOKINGS).contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(LocalTime.of(9, 0), 60, null)))
                .andExpect(status().isUnauthorized());
    }

    // --- 2.1.29, 2.1.30 Chi tiet va trang thai ----------------------------

    @Test
    @DisplayName("Chi tiet don co thong tin lien he va toa do san de chi duong")
    void detailIncludesVenueContact() throws Exception {
        String id = createAndGetId(LocalTime.of(9, 0), 60);

        mockMvc.perform(get(BOOKINGS + "/" + id).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue.phone").value("0900000000"))
                .andExpect(jsonPath("$.venue.latitude").isNotEmpty())
                .andExpect(jsonPath("$.venue.longitude").isNotEmpty())
                .andExpect(jsonPath("$.items[0].courtName").value("San A"));
    }

    @Test
    @DisplayName("Nguoi khac khong xem duoc don, tra 404 chu khong tiet lo don co ton tai")
    void otherUserCannotReadBooking() throws Exception {
        String id = createAndGetId(LocalTime.of(9, 0), 60);
        String otherToken = loginAs(persistUser("khac"));

        mockMvc.perform(get(BOOKINGS + "/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Don khong ton tai tra 404")
    void unknownBookingReturnsNotFound() throws Exception {
        mockMvc.perform(get(BOOKINGS + "/" + UUID.randomUUID()).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    // --- 2.1.31 Huy don ----------------------------------------------------

    @Test
    @DisplayName("Huy don cap nhat trang thai, ly do va them mot buoc vao timeline")
    void cancelBooking() throws Exception {
        String id = createAndGetId(LocalTime.of(9, 0), 60);

        mockMvc.perform(post(BOOKINGS + "/" + id + "/cancel").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Thay doi ke hoach ca nhan"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.cancellationReason").value("Thay doi ke hoach ca nhan"))
                .andExpect(jsonPath("$.cancellable").value(false))
                .andExpect(jsonPath("$.statusHistory.length()").value(2))
                .andExpect(jsonPath("$.items[0].status").value("cancelled"));
    }

    @Test
    @DisplayName("Huy xong thi khung gio duoc giai phong cho nguoi khac dat")
    void cancelReleasesSlot() throws Exception {
        String id = createAndGetId(LocalTime.of(9, 0), 60);

        mockMvc.perform(post(BOOKINGS + "/" + id + "/cancel").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Doi lich"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post(BOOKINGS).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(LocalTime.of(9, 0), 60, null)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Huy lan thu hai tra 409")
    void rejectsSecondCancel() throws Exception {
        String id = createAndGetId(LocalTime.of(9, 0), 60);
        cancel(id);

        mockMvc.perform(post(BOOKINGS + "/" + id + "/cancel").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Thu lai"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_CANCELLABLE"));
    }

    @Test
    @DisplayName("Thieu ly do huy tra 400")
    void rejectsCancelWithoutReason() throws Exception {
        String id = createAndGetId(LocalTime.of(9, 0), 60);

        mockMvc.perform(post(BOOKINGS + "/" + id + "/cancel").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").isNotEmpty());
    }

    @Test
    @DisplayName("Nguoi khac khong huy duoc don")
    void otherUserCannotCancel() throws Exception {
        String id = createAndGetId(LocalTime.of(9, 0), 60);
        String otherToken = loginAs(persistUser("khac2"));

        mockMvc.perform(post(BOOKINGS + "/" + id + "/cancel")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Thu"}
                                """))
                .andExpect(status().isNotFound());
    }

    // --- 2.1.32 Lich su ----------------------------------------------------

    @Test
    @DisplayName("Tab 'pending' chi liet ke don cho thanh toan")
    void listsPendingTab() throws Exception {
        createAndGetId(LocalTime.of(9, 0), 60);

        mockMvc.perform(get(BOOKINGS).header("Authorization", bearer()).param("tab", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("pending_payment"));
    }

    @Test
    @DisplayName("Don da huy chuyen sang tab 'cancelled'")
    void cancelledBookingMovesTab() throws Exception {
        cancel(createAndGetId(LocalTime.of(9, 0), 60));

        mockMvc.perform(get(BOOKINGS).header("Authorization", bearer()).param("tab", "pending"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(BOOKINGS).header("Authorization", bearer()).param("tab", "cancelled"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Tim theo ten san khong dau")
    void searchesByVenueNameWithoutDiacritics() throws Exception {
        createAndGetId(LocalTime.of(9, 0), 60);

        mockMvc.perform(get(BOOKINGS).header("Authorization", bearer()).param("q", "kiem thu dat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Chi thay don cua chinh minh")
    void listsOnlyOwnBookings() throws Exception {
        createAndGetId(LocalTime.of(9, 0), 60);
        String otherToken = loginAs(persistUser("khac3"));

        mockMvc.perform(get(BOOKINGS).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("Tab khong hop le tra 400")
    void rejectsUnknownTab() throws Exception {
        mockMvc.perform(get(BOOKINGS).header("Authorization", bearer()).param("tab", "linh-tinh"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.tab").isNotEmpty());
    }

    // --- Het han thanh toan ------------------------------------------------

    @Test
    @DisplayName("Don qua han thanh toan chuyen sang expired va giai phong khung gio")
    void expiresOverdueBooking() throws Exception {
        String id = createAndGetId(LocalTime.of(9, 0), 60);

        Booking booking = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
        booking.setExpiresAt(Instant.now().minusSeconds(60));
        bookingRepository.saveAndFlush(booking);

        bookingService.expireOverdueBookings();

        mockMvc.perform(get(BOOKINGS + "/" + id).header("Authorization", bearer()))
                .andExpect(jsonPath("$.status").value("expired"))
                .andExpect(jsonPath("$.items[0].status").value("expired"))
                // Buoc doi trang thai do he thong thuc hien, khong phai nguoi dung.
                .andExpect(jsonPath("$.statusHistory[1].bySystem").value(true));

        mockMvc.perform(post(BOOKINGS).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(LocalTime.of(9, 0), 60, null)))
                .andExpect(status().isCreated());
    }

    // --- Tien ich ----------------------------------------------------------

    private String bearer() {
        return "Bearer " + token;
    }

    private Instant at(LocalTime time) {
        return futureDate.atTime(time).atZone(ZONE).toInstant();
    }

    private String createBody(LocalTime start, int durationMinutes, String note) {
        String noteJson = note == null ? "null" : "\"" + note + "\"";
        return """
                {"courtId":"%s","startTime":"%s","durationMinutes":%d,"note":%s}
                """.formatted(court.getId(), at(start), durationMinutes, noteJson);
    }

    private String createAndGetId(LocalTime start, int durationMinutes) throws Exception {
        String response = mockMvc.perform(post(BOOKINGS).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(start, durationMinutes, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private void cancel(String id) throws Exception {
        mockMvc.perform(post(BOOKINGS + "/" + id + "/cancel").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"Doi lich"}
                                """))
                .andExpect(status().isOk());
    }

    private User persistUser(String prefix) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nguoi Dung " + prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.getUserRoles().add(new UserRole(user, roleRepository.findByCode(Role.PLAYER).orElseThrow()));
        user.setPlayerProfile(new PlayerProfile(user));
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
}
