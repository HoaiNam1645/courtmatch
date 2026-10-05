package com.courtly.api.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.BlockStatus;
import com.courtly.common.enums.BookingItemStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.CourtStatus;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtBlock;
import com.courtly.domain.venue.CourtBlockRepository;
import com.courtly.domain.venue.CourtPriceRule;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueOperatingHours;
import com.courtly.domain.venue.VenueRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Kiem thu 2.1.26 lich trong va 2.1.27 chon gio / tam tinh. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AvailabilityAndQuoteTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CourtBlockRepository courtBlockRepository;

    private Venue venue;
    private Court court;
    private User owner;
    /** Ngay thu hai cua tuan sau: luon o tuong lai va khong bi anh huong boi gio hien tai. */
    private LocalDate futureDate;

    @BeforeEach
    void seed() {
        futureDate = LocalDate.now(ZONE).plusDays(1);
        while (futureDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            futureDate = futureDate.plusDays(1);
        }

        owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setFullName("Chu San");
        owner.setEmail("owner-" + UUID.randomUUID() + "@example.com");
        owner.setStatus(UserStatus.ACTIVE);
        userRepository.save(owner);

        venue = new Venue();
        venue.setId(UUID.randomUUID());
        venue.setOwner(owner);
        venue.setName("San Kiem Thu Lich");
        venue.setSlug("kt-lich-" + UUID.randomUUID());
        venue.setStatus(VenueStatus.ACTIVE);
        venue.setApprovalStatus(ApprovalStatus.APPROVED);
        venue.setLatitude(BigDecimal.valueOf(16.05));
        venue.setLongitude(BigDecimal.valueOf(108.20));

        // Mo cua 08:00 - 12:00 moi ngay -> 4 khung mot gio.
        for (int day = 1; day <= 7; day++) {
            VenueOperatingHours hours = new VenueOperatingHours();
            hours.setId(UUID.randomUUID());
            hours.setVenue(venue);
            hours.setDayOfWeek((short) day);
            hours.setOpenTime(LocalTime.of(8, 0));
            hours.setCloseTime(LocalTime.of(12, 0));
            venue.getOperatingHours().add(hours);
        }

        court = new Court();
        court.setId(UUID.randomUUID());
        court.setVenue(venue);
        court.setName("San A");
        court.setCourtCode("A");
        court.setStatus(CourtStatus.ACTIVE);

        // 08:00-10:00 gia 100k, 10:00-12:00 gia 200k -> kiem tra cong theo doan.
        court.getPriceRules().add(priceRule(court, LocalTime.of(8, 0), LocalTime.of(10, 0), 100_000));
        court.getPriceRules().add(priceRule(court, LocalTime.of(10, 0), LocalTime.of(12, 0), 200_000));
        venue.getCourts().add(court);

        venueRepository.saveAndFlush(venue);
    }

    private CourtPriceRule priceRule(Court court, LocalTime start, LocalTime end, long price) {
        CourtPriceRule rule = new CourtPriceRule();
        rule.setId(UUID.randomUUID());
        rule.setCourt(court);
        rule.setStartTime(start);
        rule.setEndTime(end);
        rule.setPricePerHour(BigDecimal.valueOf(price));
        rule.setStatus(ActiveStatus.ACTIVE);
        rule.setEffectiveFrom(LocalDate.now(ZONE).minusYears(1));
        return rule;
    }

    private Instant at(LocalTime time) {
        return futureDate.atTime(time).atZone(ZONE).toInstant();
    }

    private String availabilityUrl() {
        return "/api/v1/venues/" + venue.getId() + "/availability";
    }

    // --- 2.1.26 Lich trong ------------------------------------------------

    @Test
    @DisplayName("Mo cua 08:00-12:00 voi thoi luong 60 phut thi co 4 khung")
    void buildsHourlySlots() throws Exception {
        mockMvc.perform(get(availabilityUrl())
                        .param("date", futureDate.toString())
                        .param("durationMinutes", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.openTime").value("08:00"))
                .andExpect(jsonPath("$.courts[0].slots.length()").value(4))
                .andExpect(jsonPath("$.courts[0].availableCount").value(4));
    }

    @Test
    @DisplayName("Thoi luong 120 phut thi khung cuoi phai ket thuc dung gio dong cua")
    void lastSlotFitsWithinClosingTime() throws Exception {
        mockMvc.perform(get(availabilityUrl())
                        .param("date", futureDate.toString())
                        .param("durationMinutes", "120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courts[0].slots.length()").value(3))
                .andExpect(jsonPath("$.courts[0].slots[2].startTime").value("10:00"))
                .andExpect(jsonPath("$.courts[0].slots[2].endTime").value("12:00"));
    }

    @Test
    @DisplayName("Gia tung khung tinh theo court_price_rules, vat qua hai khung thi cong doan")
    void slotPriceSpansBands() throws Exception {
        mockMvc.perform(get(availabilityUrl())
                        .param("date", futureDate.toString())
                        .param("durationMinutes", "90"))
                .andExpect(status().isOk())
                // 09:00-10:30 = 100.000 x 1 gio + 200.000 x 0,5 gio
                .andExpect(jsonPath("$.courts[0].slots[?(@.startTime == '09:00')].totalPrice")
                        .value(org.hamcrest.Matchers.contains(200000)));
    }

    @Test
    @DisplayName("Khung gio da co booking pending/confirmed thi bao booked")
    void marksBookedSlots() throws Exception {
        persistBooking(LocalTime.of(9, 0), LocalTime.of(10, 0), BookingItemStatus.CONFIRMED);

        mockMvc.perform(get(availabilityUrl())
                        .param("date", futureDate.toString())
                        .param("durationMinutes", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courts[0].slots[?(@.startTime == '09:00')].status")
                        .value(org.hamcrest.Matchers.contains("booked")))
                .andExpect(jsonPath("$.courts[0].availableCount").value(3));
    }

    @Test
    @DisplayName("Booking da huy khong chiem cho")
    void cancelledBookingDoesNotBlock() throws Exception {
        persistBooking(LocalTime.of(9, 0), LocalTime.of(10, 0), BookingItemStatus.CANCELLED);

        mockMvc.perform(get(availabilityUrl())
                        .param("date", futureDate.toString())
                        .param("durationMinutes", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courts[0].availableCount").value(4));
    }

    @Test
    @DisplayName("Khung gio nam trong court_blocks thi bao blocked")
    void marksBlockedSlots() throws Exception {
        CourtBlock block = new CourtBlock();
        block.setId(UUID.randomUUID());
        block.setCourt(court);
        block.setStartTime(at(LocalTime.of(10, 0)));
        block.setEndTime(at(LocalTime.of(11, 0)));
        block.setReason("Bao tri");
        block.setStatus(BlockStatus.ACTIVE);
        courtBlockRepository.saveAndFlush(block);

        mockMvc.perform(get(availabilityUrl())
                        .param("date", futureDate.toString())
                        .param("durationMinutes", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courts[0].slots[?(@.startTime == '10:00')].status")
                        .value(org.hamcrest.Matchers.contains("blocked")));
    }

    @Test
    @DisplayName("Ngay venue nghi thi tra closed va khong co san con nao")
    void closedDayReturnsNoCourts() throws Exception {
        venue.getOperatingHours().forEach(hours -> hours.setClosed(true));
        venueRepository.saveAndFlush(venue);

        mockMvc.perform(get(availabilityUrl())
                        .param("date", futureDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true))
                .andExpect(jsonPath("$.courts").isEmpty());
    }

    @Test
    @DisplayName("Xem lich ngay da qua tra 400")
    void rejectsPastDate() throws Exception {
        mockMvc.perform(get(availabilityUrl())
                        .param("date", LocalDate.now(ZONE).minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.date").isNotEmpty());
    }

    @Test
    @DisplayName("Xem lich qua xa tra 400")
    void rejectsFarFutureDate() throws Exception {
        mockMvc.perform(get(availabilityUrl())
                        .param("date", LocalDate.now(ZONE).plusYears(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.date").isNotEmpty());
    }

    @Test
    @DisplayName("San khong ton tai tra 404")
    void unknownVenueReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/venues/" + UUID.randomUUID() + "/availability")
                        .param("date", futureDate.toString()))
                .andExpect(status().isNotFound());
    }

    // --- 2.1.27 Tam tinh ---------------------------------------------------

    @Test
    @DisplayName("Tam tinh khung gio trong tra ve tong tien va chi tiet tung doan")
    void quoteReturnsSegments() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(LocalTime.of(9, 0), 90)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.totalAmount").value(200000))
                .andExpect(jsonPath("$.segments.length()").value(2))
                .andExpect(jsonPath("$.segments[0].amount").value(100000))
                .andExpect(jsonPath("$.segments[1].amount").value(100000));
    }

    @Test
    @DisplayName("Tam tinh khung gio da co nguoi dat thi bao khong dat duoc")
    void quoteRejectsBookedSlot() throws Exception {
        persistBooking(LocalTime.of(9, 0), LocalTime.of(10, 0), BookingItemStatus.PENDING);

        mockMvc.perform(post("/api/v1/bookings/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(LocalTime.of(9, 0), 60)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.unavailableReason").isNotEmpty());
    }

    @Test
    @DisplayName("Tam tinh khung gio ngoai gio mo cua thi bao khong dat duoc")
    void quoteRejectsOutsideOpeningHours() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(LocalTime.of(20, 0), 60)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    @DisplayName("Thoi luong ngoai 60/90/120 tra 400")
    void quoteRejectsOtherDurations() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quoteBody(LocalTime.of(9, 0), 45)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").isNotEmpty());
    }

    @Test
    @DisplayName("Gio bat dau o qua khu tra 400")
    void quoteRejectsPastStart() throws Exception {
        String body = """
                {"courtId":"%s","startTime":"%s","durationMinutes":60}
                """.formatted(court.getId(), Instant.now().minusSeconds(3600));

        mockMvc.perform(post("/api/v1/bookings/quote")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.startTime").isNotEmpty());
    }

    @Test
    @DisplayName("San con khong ton tai tra 404")
    void quoteRejectsUnknownCourt() throws Exception {
        String body = """
                {"courtId":"%s","startTime":"%s","durationMinutes":60}
                """.formatted(UUID.randomUUID(), at(LocalTime.of(9, 0)));

        mockMvc.perform(post("/api/v1/bookings/quote")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    // --- Tien ich ----------------------------------------------------------

    private String quoteBody(LocalTime startTime, int durationMinutes) {
        return """
                {"courtId":"%s","startTime":"%s","durationMinutes":%d}
                """.formatted(court.getId(), at(startTime), durationMinutes);
    }

    private void persistBooking(LocalTime start, LocalTime end, BookingItemStatus status) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setBookingCode("KT-" + UUID.randomUUID().toString().substring(0, 8));
        booking.setUser(owner);
        booking.setVenue(venue);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalAmount(BigDecimal.valueOf(100_000));

        BookingItem item = new BookingItem();
        item.setId(UUID.randomUUID());
        item.setBooking(booking);
        item.setCourt(court);
        item.setStartTime(at(start));
        item.setEndTime(at(end));
        item.setPrice(BigDecimal.valueOf(100_000));
        item.setStatus(status);
        booking.getItems().add(item);

        bookingRepository.saveAndFlush(booking);
    }
}
