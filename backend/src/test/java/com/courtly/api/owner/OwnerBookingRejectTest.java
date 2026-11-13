package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingItemStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.14 - Tu choi yeu cau dat san. */
class OwnerBookingRejectTest extends OwnerApiTestBase {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private String ownerToken;
    private Court court;
    private String playerToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        User owner = persistOwner("owner-reject");
        ownerToken = loginAs(owner);
        Venue venue = persistVenue(owner, "San Tu Choi");
        court = venue.getCourts().getFirst();
        playerToken = loginAs(persistUser("player", Role.PLAYER));
    }

    @Test
    @DisplayName("Danh muc ly do lay tu API, chi \"other\" bat buoc ghi chu")
    void reasonCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/owner/booking-requests/reject-reasons").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasons[*].code", hasItem("maintenance")))
                .andExpect(jsonPath("$.reasons[?(@.code=='other')].noteRequired").value(true))
                .andExpect(jsonPath("$.reasons[?(@.code=='maintenance')].noteRequired").value(false));
    }

    @Test
    @DisplayName("Tu choi: sang rejected, luu ly do, giai phong khung gio de nguoi khac dat, ghi audit va lich su")
    void rejectReleasesSlot() throws Exception {
        String id = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));

        mockMvc.perform(post(path(id)).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"maintenance\",\"note\":\"  Thay luoi moi  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.rejectionReason").value("San dang bao tri hoac sua chua: Thay luoi moi"))
                .andExpect(jsonPath("$.ownerRespondedByName").exists());

        Booking saved = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(saved.getRejectedAt()).isNotNull();
        assertThat(saved.getItems()).allMatch(item -> item.getStatus() == BookingItemStatus.CANCELLED);
        assertThat(saved.getStatusHistory()).anySatisfy(history ->
                assertThat(history.getReason()).contains("Thay luoi moi"));
        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("booking.reject");
            assertThat(log.getNewData()).containsEntry("reasonCode", "maintenance");
        });

        // Khung gio duoc giai phong ngay: nguoi choi khac dat lai duoc.
        mockMvc.perform(post("/api/v1/bookings/quote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courtId\":\"%s\",\"startTime\":\"%s\",\"durationMinutes\":90}".formatted(
                                court.getId(), nextMonday().atTime(9, 0).atZone(ZONE).toInstant())))
                .andExpect(jsonPath("$.available").value(true));
        createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));

        // Nguoi choi thay ly do.
        mockMvc.perform(get("/api/v1/bookings/" + id).header("Authorization", bearer(playerToken)))
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.statusHistory[?(@.newStatus=='rejected')].reason").value(hasItem(
                        "San dang bao tri hoac sua chua: Thay luoi moi")));
    }

    @Test
    @DisplayName("Bat buoc chon ly do hop le; ly do khac bat buoc ghi chu; ghi chu toi da 500 ky tu")
    void validation() throws Exception {
        String id = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        expect400(id, "{}", "reasonCode");
        expect400(id, "{\"reasonCode\":\"  \"}", "reasonCode");
        expect400(id, "{\"reasonCode\":\"khong-co\"}", "reasonCode");
        expect400(id, "{\"reasonCode\":\"other\"}", "note");
        expect400(id, "{\"reasonCode\":\"other\",\"note\":\"ab\"}", "note");
        expect400(id, "{\"reasonCode\":\"maintenance\",\"note\":\"" + "x".repeat(501) + "\"}", "note");
        mockMvc.perform(post(path(id)).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
        assertThat(bookingRepository.findById(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_OWNER_CONFIRMATION);

        mockMvc.perform(post(path(id)).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"other\",\"note\":\"Chu san di vang\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectionReason").value("Ly do khac: Chu san di vang"));
    }

    @Test
    @DisplayName("Tu choi lan hai / don da xac nhan: 409; don cua chu san khac: 404; nguoi choi 403")
    void invalidStatesAndOwnership() throws Exception {
        String id = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        String body = "{\"reasonCode\":\"slot_unavailable\"}";
        String otherToken = loginAs(persistOwner("other"));
        mockMvc.perform(post(path(id)).header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(path(id)).header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(path(id)).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post(path(id)).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_REVIEW_INVALID"));

        String accepted = createBooking(playerToken, court, nextMonday(), LocalTime.of(12, 0));
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + accepted + "/accept").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post(path(accepted)).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(post(path(UUID.randomUUID().toString())).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    private void expect400(String id, String body, String field) throws Exception {
        mockMvc.perform(post(path(id)).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors." + field).exists());
    }

    private static String path(String bookingId) {
        return "/api/v1/owner/booking-requests/" + bookingId + "/reject";
    }
}
