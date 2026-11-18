package com.courtly.api.dispute;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.api.owner.OwnerApiTestBase;
import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** Nen chung cho test bao cao don dat san (2.3.85 - 2.3.92): chu san, san, nguoi choi va ham tao don o trang thai/thoi diem cho truoc. */
abstract class BookingDisputeTestBase extends com.courtly.api.admin.AdminApiTestBase {

    @Autowired protected BookingRepository bookingRepository;
    @Autowired protected JdbcTemplate jdbc;
    @PersistenceContext protected EntityManager entityManager;

    protected User owner;
    protected String ownerToken;
    protected Venue venue;
    protected Court court;
    protected User player;
    protected String playerToken;
    private int slot;

    @BeforeEach
    void setUpDisputes() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-dispute");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Bao Cao");
        court = venue.getCourts().getFirst();
        player = persistUser("reporter", Role.PLAYER);
        playerToken = loginAs(player);
        slot = 0;
    }

    /** Don cua {@code token} o trang thai cho truoc, gio choi ket thuc {@code endedDaysAgo} ngay truoc; tra ve bookingId. */
    protected UUID booking(String token, BookingStatus status, double endedDaysAgo) throws Exception {
        String id = createBooking(token, court, nextMonday(), LocalTime.of(6, 0).plusMinutes(100L * slot++));
        Booking booking = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
        booking.setStatus(status);
        bookingRepository.saveAndFlush(booking);
        // Dich moi don mot khoang rieng de khong dung rang buoc chong trung gio cua san.
        long shiftMinutes = 200L * slot;
        jdbc.update("""
                UPDATE booking_items
                SET start_time = now() - make_interval(secs => ? * 86400 + 5400 + ? * 60),
                    end_time = now() - make_interval(secs => ? * 86400 + ? * 60)
                WHERE booking_id = ?
                """, endedDaysAgo, shiftMinutes, endedDaysAgo, shiftMinutes, booking.getId());
        entityManager.clear();
        return booking.getId();
    }

    protected JsonNode dispute(String token, UUID bookingId, int expectedStatus, String category, String description) throws Exception {
        return dispute(token, bookingId, expectedStatus, category, description, null);
    }

    /** {@code extraJson}: them truong vao JSON, vi du {@code "refundRequested":true,"refundAmount":50000}. */
    protected JsonNode dispute(String token, UUID bookingId, int expectedStatus, String category, String description, String extraJson) throws Exception {
        String body = "{\"category\":" + (category == null ? "null" : "\"" + category + "\"")
                + ",\"description\":" + (description == null ? "null" : "\"" + description + "\"")
                + (extraJson == null ? "" : "," + extraJson) + "}";
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/disputes")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString());
    }

    /** Khoan thanh toan cua don (tra tien cho chu san), o trang thai cho truoc. */
    protected void payment(UUID bookingId, String status) {
        jdbc.update("INSERT INTO payments (booking_id, amount, status, recipient_owner_id, method) VALUES (?, 180000, ?, ?, 'cash')",
                bookingId, status, owner.getId());
        entityManager.clear();
    }
}
