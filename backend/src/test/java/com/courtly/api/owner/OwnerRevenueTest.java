package com.courtly.api.owner;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.17 - Thong ke doanh thu cua san. */
class OwnerRevenueTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/owner/revenue";

    @Autowired private PaymentRepository paymentRepository;

    private User owner;
    private String ownerToken;
    private Venue venue;
    private Court court;
    private String playerToken;
    private LocalDate today;
    private int slot;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-revenue");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Doanh Thu");
        court = venue.getCourts().getFirst();
        playerToken = loginAs(persistUser("player", Role.PLAYER));
        today = LocalDate.now(ZONE);
        slot = 6;
    }

    @Test
    @DisplayName("Doanh thu gop = tien chu san da xac nhan thu; don chua thu khong tinh; mac dinh 30 ngay, du moi ngay")
    void countsOnlyConfirmedPayments() throws Exception {
        collect(court, daysAgo(0, 10));
        collect(court, daysAgo(3, 12));
        // Da chap nhan nhung chua thu tien: khong tinh.
        String unpaid = createBookingAt(court, nextMonday());
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + unpaid + "/accept").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.to").value(today.toString()))
                .andExpect(jsonPath("$.totalRevenue").value(360000))
                .andExpect(jsonPath("$.bookingCount").value(2))
                .andExpect(jsonPath("$.averagePerBooking").value(180000))
                .andExpect(jsonPath("$.daily", hasSize(30)))
                .andExpect(jsonPath("$.daily[29].date").value(today.toString()))
                .andExpect(jsonPath("$.daily[29].revenue").value(180000))
                .andExpect(jsonPath("$.daily[26].revenue").value(180000))
                .andExpect(jsonPath("$.daily[27].revenue").value(0))
                .andExpect(jsonPath("$.byVenue[0].venueName").value("San Doanh Thu"))
                .andExpect(jsonPath("$.byVenue[0].revenue").value(360000));
    }

    @Test
    @DisplayName("So voi ky truoc co do dai bang nhau: tang / giam %, ky truoc bang 0 thi khong co %")
    void comparesWithPreviousPeriod() throws Exception {
        collect(court, daysAgo(1, 10));
        collect(court, daysAgo(2, 12));
        collect(court, daysAgo(9, 10));

        // Ky 7 ngay gan nhat: 2 don; 7 ngay truoc do: 1 don -> tang 100%.
        mockMvc.perform(get(PATH).param("from", today.minusDays(6).toString()).param("to", today.toString())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalRevenue").value(360000))
                .andExpect(jsonPath("$.previous.from").value(today.minusDays(13).toString()))
                .andExpect(jsonPath("$.previous.to").value(today.minusDays(7).toString()))
                .andExpect(jsonPath("$.previous.totalRevenue").value(180000))
                .andExpect(jsonPath("$.previous.bookingCount").value(1))
                .andExpect(jsonPath("$.changePercent").value(100.0));

        mockMvc.perform(get(PATH).param("from", today.minusDays(3).toString()).param("to", today.toString())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.previous.totalRevenue").value(0))
                .andExpect(jsonPath("$.changePercent").doesNotExist());
    }

    @Test
    @DisplayName("Tinh theo ngay xac nhan o gio Viet Nam: 23:30 hom qua thuoc hom qua, 00:30 hom nay thuoc hom nay")
    void vietnamDayBoundary() throws Exception {
        Instant startOfToday = today.atStartOfDay(ZONE).toInstant();
        collect(court, startOfToday.minusSeconds(30 * 60));
        collect(court, startOfToday.plusSeconds(30 * 60));

        mockMvc.perform(get(PATH).param("from", today.minusDays(1).toString()).param("to", today.toString())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.daily[0].date").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.daily[0].bookings").value(1))
                .andExpect(jsonPath("$.daily[1].bookings").value(1));
        mockMvc.perform(get(PATH).param("from", today.toString()).param("to", today.toString())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.bookingCount").value(1))
                .andExpect(jsonPath("$.daily", hasSize(1)));
    }

    @Test
    @DisplayName("Chia theo san, loc mot san; chu san khac khong thay doanh thu cua minh")
    void perVenueAndOwnership() throws Exception {
        Venue second = persistVenue(owner, "San Thu Hai");
        collect(court, daysAgo(0, 9));
        collect(second.getCourts().getFirst(), daysAgo(0, 10));
        collect(second.getCourts().getFirst(), daysAgo(1, 10));

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalRevenue").value(540000))
                .andExpect(jsonPath("$.byVenue", hasSize(2)))
                .andExpect(jsonPath("$.byVenue[0].venueName").value("San Thu Hai"))
                .andExpect(jsonPath("$.byVenue[0].revenue").value(360000))
                .andExpect(jsonPath("$.byVenue[0].bookings").value(2))
                .andExpect(jsonPath("$.byVenue[1].venueName").value("San Doanh Thu"));
        mockMvc.perform(get(PATH).param("venueId", venue.getId().toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalRevenue").value(180000))
                .andExpect(jsonPath("$.byVenue", hasSize(1)));

        String otherToken = loginAs(persistOwner("other"));
        mockMvc.perform(get(PATH).header("Authorization", bearer(otherToken)))
                .andExpect(jsonPath("$.totalRevenue").value(0))
                .andExpect(jsonPath("$.byVenue", hasSize(0)))
                .andExpect(jsonPath("$.daily", hasSize(30)));
        mockMvc.perform(get(PATH).param("venueId", venue.getId().toString()).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Kiem tra tham so: khoang ngay dao nguoc, qua 366 ngay, sai dinh dang; nguoi choi 403")
    void validation() throws Exception {
        mockMvc.perform(get(PATH).param("from", "2026-12-10").param("to", "2026-12-01").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.to").exists());
        mockMvc.perform(get(PATH).param("from", "2024-01-01").param("to", "2026-01-01").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.to").exists());
        mockMvc.perform(get(PATH).param("from", "01/12/2026").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("venueId", "khong-uuid").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ helpers

    private Instant daysAgo(int days, int hour) {
        return today.minusDays(days).atTime(hour, 0).atZone(ZONE).toInstant();
    }

    /** Dat booking, chap nhan, nguoi choi bao tra, chu san xac nhan thu; roi dat lai moc xac nhan. */
    private void collect(Court target, Instant confirmedAt) throws Exception {
        String bookingId = createBookingAt(target, nextMonday());
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + bookingId + "/accept").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(UUID.fromString(bookingId)).orElseThrow();
        mockMvc.perform(post("/api/v1/payments/" + payment.getId() + "/report-paid").header("Authorization", bearer(playerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"cash\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/" + payment.getId() + "/confirm-received").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        payment.setConfirmedAt(confirmedAt);
        paymentRepository.saveAndFlush(payment);
    }

    /** Moi lan dat mot khung 90 phut khac nhau (180.000d) de khong dung do nhau. */
    private String createBookingAt(Court target, LocalDate date) throws Exception {
        LocalTime start = LocalTime.of(6, 0).plusMinutes(100L * (slot++ - 6));
        return createBooking(playerToken, target, date, start);
    }
}
