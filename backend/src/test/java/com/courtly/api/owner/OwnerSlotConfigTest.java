package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtPriceRule;
import com.courtly.domain.venue.Venue;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.9 - Tao cac khung gio san. */
class OwnerSlotConfigTest extends OwnerApiTestBase {

    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private BookingRepository bookingRepository;

    private String ownerToken;
    private Venue venue;
    private Court court;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        User owner = persistOwner("owner-slots");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Khung Gio");
        court = venue.getCourts().getFirst();
    }

    @Test
    @DisplayName("Mac dinh 60 phut: xem truoc 16 khung moi ngay (06:00-22:00), du gia")
    void defaultPreview() throws Exception {
        mockMvc.perform(get(path()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepMinutes").value(60))
                .andExpect(jsonPath("$.allowedSteps[0]").value(30))
                .andExpect(jsonPath("$.days", hasSize(7)))
                .andExpect(jsonPath("$.days[0].slotCount").value(16))
                .andExpect(jsonPath("$.days[0].unpricedSlots").isEmpty());
    }

    @Test
    @DisplayName("Doi sang 30 phut: gap doi khung, lich nguoi choi va lich chu san deu theo buoc moi, ghi audit")
    void switchToThirtyMinutes() throws Exception {
        mockMvc.perform(put(path())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stepMinutes\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepMinutes").value(30))
                .andExpect(jsonPath("$.days[0].slotCount").value(32));

        mockMvc.perform(get("/api/v1/venues/" + venue.getId() + "/availability")
                        .param("date", nextMonday().toString()).param("durationMinutes", "60"))
                .andExpect(jsonPath("$.courts[0].slots[0].startTime").value("06:00"))
                .andExpect(jsonPath("$.courts[0].slots[1].startTime").value("06:30"));
        mockMvc.perform(get("/api/v1/owner/venues/" + venue.getId() + "/schedule")
                        .param("date", nextMonday().toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.stepMinutes").value(30))
                .andExpect(jsonPath("$.days[0].courts[0].slots", hasSize(32)));

        assertThat(venueRepository.findById(venue.getId()).orElseThrow().getSlotStepMinutes()).isEqualTo((short) 30);
        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("slot_step.update");
            assertThat(log.getOldData()).containsEntry("stepMinutes", 60);
            assertThat(log.getNewData()).containsEntry("stepMinutes", 30);
        });
    }

    @Test
    @DisplayName("Khung chua co gia duoc bao theo san con; ngay nghi khong co khung")
    void unpricedSlotsAndClosedDays() throws Exception {
        CourtPriceRule rule = court.getPriceRules().getFirst();
        rule.setEndTime(LocalTime.of(20, 0));
        court.getPriceRules().clear();
        court.getPriceRules().add(rule);
        venueRepository.saveAndFlush(venue);
        venue.getOperatingHours().stream().filter(hours -> hours.getDayOfWeek() == 7).forEach(hours -> {
            hours.setClosed(true);
            hours.setOpenTime(null);
            hours.setCloseTime(null);
        });
        venueRepository.saveAndFlush(venue);

        mockMvc.perform(get(path()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.days[0].unpricedSlots.A").value(2))
                .andExpect(jsonPath("$.days[6].closed").value(true))
                .andExpect(jsonPath("$.days[6].slotCount").value(0));
    }

    @Test
    @DisplayName("Doi buoc khong huy booking da tao; buoc la / thieu / sai kieu bi tu choi; san nguoi khac 404")
    void validationAndOwnership() throws Exception {
        User player = persistUser("player", Role.PLAYER);
        String bookingId = createBooking(loginAs(player), court, nextMonday(), LocalTime.of(9, 0));
        mockMvc.perform(put(path()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stepMinutes\":30}"))
                .andExpect(status().isOk());
        assertThat(bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow().getStatus().getValue())
                .isEqualTo("pending_owner_confirmation");

        for (String body : new String[] {"{\"stepMinutes\":45}", "{\"stepMinutes\":0}", "{\"stepMinutes\":-30}"}) {
            mockMvc.perform(put(path()).header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.stepMinutes").exists());
        }
        mockMvc.perform(put(path()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.stepMinutes").exists());
        mockMvc.perform(put(path()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stepMinutes\":\"abc\"}"))
                .andExpect(status().isBadRequest());

        Venue other = persistVenue(persistOwner("other"), "San Nguoi Khac");
        mockMvc.perform(put("/api/v1/owner/venues/" + other.getId() + "/slot-config")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stepMinutes\":30}"))
                .andExpect(status().isNotFound());
        assertThat(venueRepository.findById(other.getId()).orElseThrow().getSlotStepMinutes()).isEqualTo((short) 60);
    }

    private String path() {
        return "/api/v1/owner/venues/" + venue.getId() + "/slot-config";
    }
}
