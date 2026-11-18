package com.courtly.api.review;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.courtly.api.admin.AdminApiTestBase;
import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** Nen chung cho test danh gia san (2.3.77 - 2.3.84): chu san, san, nguoi choi va ham tao don da hoan thanh. */
abstract class VenueReviewTestBase extends AdminApiTestBase {

    @Autowired protected BookingRepository bookingRepository;
    @Autowired protected JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext protected jakarta.persistence.EntityManager entityManager;

    protected User owner;
    protected String ownerToken;
    protected Venue venue;
    protected Court court;
    protected User player;
    protected String playerToken;
    private int slot;

    @BeforeEach
    void setUpReviews() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-review");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Danh Gia");
        court = venue.getCourts().getFirst();
        player = persistUser("reviewer", Role.PLAYER);
        playerToken = loginAs(player);
        slot = 0;
    }

    /** Don cua {@code token} da hoan thanh {@code daysAgo} ngay truoc; tra ve bookingId. */
    protected UUID completedBooking(String token, long daysAgo) throws Exception {
        String id = createBooking(token, court, nextMonday(), LocalTime.of(6, 0).plusMinutes(100L * slot++));
        Booking booking = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        bookingRepository.saveAndFlush(booking);
        return booking.getId();
    }

    protected JsonNode review(String token, UUID bookingId, int expectedStatus, String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/venue-review")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    protected static String body(int rating, String comment) {
        return "{\"rating\":" + rating + ",\"comment\":" + (comment == null ? "null" : "\"" + comment + "\"") + "}";
    }

    protected double venueRating() {
        return jdbc.queryForObject("SELECT average_rating FROM venues WHERE id = ?", Double.class, venue.getId());
    }

    protected int venueReviewCount() {
        return jdbc.queryForObject("SELECT review_count FROM venues WHERE id = ?", Integer.class, venue.getId());
    }
}
