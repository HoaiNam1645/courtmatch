package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.12 - Xem cac yeu cau dat san. */
class OwnerBookingRequestTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/owner/booking-requests";

    @Autowired private BookingRepository bookingRepository;

    private User owner;
    private String ownerToken;
    private Venue venue;
    private Court court;
    private User player;
    private String playerToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-requests");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Yeu Cau");
        court = venue.getCourts().getFirst();
        player = persistUser("player", Role.PLAYER);
        player.setPhone("0911000222");
        userRepository.saveAndFlush(player);
        playerToken = loginAs(player);
    }

    @Test
    @DisplayName("Danh sach mac dinh la yeu cau cho duyet, day du thong tin nguoi dat, san con, gia, ghi chu, han phan hoi")
    void listPending() throws Exception {
        String id = createBookingWithNote(LocalTime.of(9, 0), "Mang theo vot");

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].bookingId").value(id))
                .andExpect(jsonPath("$.content[0].status").value("pending_owner_confirmation"))
                .andExpect(jsonPath("$.content[0].venueName").value("San Yeu Cau"))
                .andExpect(jsonPath("$.content[0].playerName").value(player.getFullName()))
                .andExpect(jsonPath("$.content[0].playerPhone").value("0911000222"))
                .andExpect(jsonPath("$.content[0].slots[0].courtName").value("San A"))
                .andExpect(jsonPath("$.content[0].totalAmount").value(180000))
                .andExpect(jsonPath("$.content[0].note").value("Mang theo vot"))
                .andExpect(jsonPath("$.content[0].respondBy").exists())
                .andExpect(jsonPath("$.content[0].secondsRemaining").isNumber())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.awaitingCount").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Yeu cau sap het han xep truoc va duoc danh dau; yeu cau da qua han nhan 0 giay")
    void expiringFirst() throws Exception {
        String later = createBookingWithNote(LocalTime.of(9, 0), null);
        String soon = createBookingWithNote(LocalTime.of(12, 0), null);
        setDeadline(later, Instant.now().plusSeconds(25 * 60));
        setDeadline(soon, Instant.now().plusSeconds(3 * 60));

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content[0].bookingId").value(soon))
                .andExpect(jsonPath("$.content[0].expiringSoon").value(true))
                .andExpect(jsonPath("$.content[1].bookingId").value(later))
                .andExpect(jsonPath("$.content[1].expiringSoon").value(false))
                .andExpect(jsonPath("$.awaitingCount").value(2))
                .andExpect(jsonPath("$.expiringSoonCount").value(1));

        setDeadline(soon, Instant.now().minusSeconds(60));
        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content[0].bookingId").value(soon))
                .andExpect(jsonPath("$.content[0].secondsRemaining").value(0))
                .andExpect(jsonPath("$.content[0].expiringSoon").value(false));
    }

    @Test
    @DisplayName("Loc theo trang thai, san va ngay choi; phan trang; don da xu ly khong con han")
    void filters() throws Exception {
        String accepted = createBookingWithNote(LocalTime.of(9, 0), null);
        mockMvc.perform(post("/api/v1/bookings/" + accepted + "/accept").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        String pending = createBookingWithNote(LocalTime.of(14, 0), null);
        Venue second = persistVenue(owner, "San Thu Hai");
        String otherVenueBooking = createBooking(playerToken, second.getCourts().getFirst(), nextMonday().plusDays(1), LocalTime.of(10, 0));

        mockMvc.perform(get(PATH).param("status", "awaiting_payment").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].bookingId").value(accepted))
                .andExpect(jsonPath("$.content[0].respondBy").doesNotExist())
                .andExpect(jsonPath("$.content[0].secondsRemaining").value(0));
        mockMvc.perform(get(PATH).param("status", "all").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get(PATH).param("venueId", venue.getId().toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].bookingId").value(pending));
        mockMvc.perform(get(PATH).param("date", nextMonday().plusDays(1).toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].bookingId").value(otherVenueBooking));
        mockMvc.perform(get(PATH).param("date", nextMonday().plusDays(3).toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.awaitingCount").value(2));
        mockMvc.perform(get(PATH).param("status", "all").param("size", "2").param("page", "1")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("Chi thay yeu cau cua san minh; san cua chu san khac 404; tham so sai 400")
    void ownershipAndValidation() throws Exception {
        createBookingWithNote(LocalTime.of(9, 0), null);
        User other = persistOwner("other");
        Venue otherVenue = persistVenue(other, "San Nguoi Khac");
        createBooking(playerToken, otherVenue.getCourts().getFirst(), nextMonday(), LocalTime.of(9, 0));

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(PATH).param("venueId", otherVenue.getId().toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PATH).param("venueId", "khong-phai-uuid").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("status", "bay-ba").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
        mockMvc.perform(get(PATH).param("date", "13/11/2026").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("size", "101").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("page", "-1").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(playerToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        // Chi xem: khong co thao tac ghi tai endpoint nay.
        mockMvc.perform(post(PATH).header("Authorization", bearer(ownerToken)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Chu san chua co san nao thi danh sach rong")
    void ownerWithoutVenues() throws Exception {
        User lonely = persistOwner("lonely");
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(lonely))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private String createBookingWithNote(LocalTime start, String note) throws Exception {
        String response = mockMvc.perform(post("/api/v1/bookings").header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courtId\":\"%s\",\"startTime\":\"%s\",\"durationMinutes\":90,\"note\":%s}".formatted(
                                court.getId(), nextMonday().atTime(start).atZone(ZONE).toInstant(),
                                note == null ? "null" : "\"" + note + "\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private void setDeadline(String bookingId, Instant deadline) {
        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        booking.setOwnerResponseExpiresAt(deadline);
        bookingRepository.saveAndFlush(booking);
        assertThat(booking.getOwnerResponseExpiresAt()).isEqualTo(deadline);
    }
}
