package com.courtly.api.owner;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.15 - Xem lich su dat san. */
class OwnerBookingHistoryTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/owner/bookings";

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
        owner = persistOwner("owner-history");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Lich Su");
        court = venue.getCourts().getFirst();
        player = persistUser("player", Role.PLAYER);
        player.setFullName("Nguyễn Văn Tâm");
        player.setPhone("0911000333");
        userRepository.saveAndFlush(player);
        playerToken = loginAs(player);
    }

    @Test
    @DisplayName("Lich su gom moi trang thai, moi nhat truoc, co nguoi dat, san con, so tien, trang thai thanh toan")
    void listAll() throws Exception {
        String pending = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        String accepted = createBooking(playerToken, court, nextMonday(), LocalTime.of(12, 0));
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + accepted + "/accept").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].bookingId").value(accepted))
                .andExpect(jsonPath("$.content[0].status").value("awaiting_payment"))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("awaiting_payment"))
                .andExpect(jsonPath("$.content[0].playerName").value("Nguyễn Văn Tâm"))
                .andExpect(jsonPath("$.content[0].playerPhone").value("0911000333"))
                .andExpect(jsonPath("$.content[0].slots[0].courtName").value("San A"))
                .andExpect(jsonPath("$.content[0].totalAmount").value(180000))
                .andExpect(jsonPath("$.content[1].bookingId").value(pending))
                .andExpect(jsonPath("$.content[1].paymentStatus").doesNotExist());
    }

    @Test
    @DisplayName("Loc theo nhom trang thai, tu khoa (ma don / ten khong dau), khoang ngay choi, san; phan trang")
    void filters() throws Exception {
        String open = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        String rejected = createBooking(playerToken, court, nextMonday(), LocalTime.of(12, 0));
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + rejected + "/reject").header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"maintenance\"}")).andExpect(status().isOk());
        String nextDay = createBooking(playerToken, court, nextMonday().plusDays(1), LocalTime.of(9, 0));
        Venue second = persistVenue(owner, "San Thu Hai");
        String otherVenue = createBooking(playerToken, second.getCourts().getFirst(), nextMonday(), LocalTime.of(18, 0));
        Booking disputed = bookingRepository.findById(UUID.fromString(otherVenue)).orElseThrow();
        disputed.setStatus(BookingStatus.DISPUTED);
        bookingRepository.saveAndFlush(disputed);

        mockMvc.perform(get(PATH).param("group", "active").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(PATH).param("group", "closed").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].bookingId").value(rejected));
        mockMvc.perform(get(PATH).param("group", "disputed").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content[0].bookingId").value(otherVenue));
        mockMvc.perform(get(PATH).param("group", "completed").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(0));

        String code = bookingRepository.findById(UUID.fromString(open)).orElseThrow().getBookingCode();
        mockMvc.perform(get(PATH).param("q", code.toLowerCase()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].bookingId").value(open));
        mockMvc.perform(get(PATH).param("q", "nguyen van tam").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(4));
        mockMvc.perform(get(PATH).param("q", "khong ai ten vay").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get(PATH).param("from", nextMonday().plusDays(1).toString()).param("to", nextMonday().plusDays(1).toString())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].bookingId").value(nextDay));
        mockMvc.perform(get(PATH).param("venueId", venue.getId().toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get(PATH).param("size", "2").param("page", "1").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("Chi tiet: timeline cac buoc, nguoi thuc hien, thanh toan; ly do tu choi")
    void detailWithTimeline() throws Exception {
        String id = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + id + "/accept").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get(PATH + "/" + id).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(id))
                .andExpect(jsonPath("$.venueName").value("San Lich Su"))
                .andExpect(jsonPath("$.playerName").value("Nguyễn Văn Tâm"))
                .andExpect(jsonPath("$.payment.status").value("awaiting_payment"))
                .andExpect(jsonPath("$.payment.method").value("owner_bank_transfer"))
                .andExpect(jsonPath("$.timeline", hasSize(2)))
                .andExpect(jsonPath("$.timeline[0].newStatus").value("pending_owner_confirmation"))
                .andExpect(jsonPath("$.timeline[0].actorName").value("Nguyễn Văn Tâm"))
                .andExpect(jsonPath("$.timeline[1].newStatus").value("awaiting_payment"))
                .andExpect(jsonPath("$.timeline[1].actorName").value(owner.getFullName()));

        String rejected = createBooking(playerToken, court, nextMonday(), LocalTime.of(14, 0));
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + rejected + "/reject").header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"private_event\"}")).andExpect(status().isOk());
        mockMvc.perform(get(PATH + "/" + rejected).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.rejectionReason").value("San dang phuc vu su kien hoac giai dau"))
                .andExpect(jsonPath("$.payment").doesNotExist());
    }

    @Test
    @DisplayName("Chi thay don cua san minh; tham so sai 400; nguoi choi 403")
    void ownershipAndValidation() throws Exception {
        String id = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        User other = persistOwner("other");
        String otherToken = loginAs(other);
        mockMvc.perform(get(PATH).header("Authorization", bearer(otherToken)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(PATH + "/" + id).header("Authorization", bearer(otherToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH).param("venueId", venue.getId().toString()).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/" + UUID.randomUUID()).header("Authorization", bearer(ownerToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/khong-phai-uuid").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());

        mockMvc.perform(get(PATH).param("group", "bay").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.group").exists());
        mockMvc.perform(get(PATH).param("from", "2026-12-10").param("to", "2026-12-01").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.to").exists());
        mockMvc.perform(get(PATH).param("from", "10/12/2026").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("q", "x".repeat(101)).header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("size", "101").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }
}
