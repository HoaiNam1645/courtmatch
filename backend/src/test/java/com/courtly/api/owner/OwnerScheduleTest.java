package com.courtly.api.owner;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.anyOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BlockStatus;
import com.courtly.common.enums.BookingItemStatus;
import com.courtly.common.enums.CourtStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.BookingItemRepository;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtBlock;
import com.courtly.domain.venue.CourtBlockRepository;
import com.courtly.domain.venue.CourtRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueOperatingHoursRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 2.3.8 - Quan ly lich san. */
class OwnerScheduleTest extends OwnerApiTestBase {

    @Autowired private CourtBlockRepository blockRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingItemRepository bookingItemRepository;
    @Autowired private VenueOperatingHoursRepository hoursRepository;

    private User owner;
    private String ownerToken;
    private Venue venue;
    private Court court;
    private LocalDate monday;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-schedule");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Lich");
        court = venue.getCourts().getFirst();
        monday = nextMonday();
    }

    @Test
    @DisplayName("Lich mot ngay: o 60 phut tu gio mo den gio dong, con trong kem gia")
    void dayScheduleAvailable() throws Exception {
        mockMvc.perform(get(path()).param("date", monday.toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stepMinutes").value(60))
                .andExpect(jsonPath("$.days", hasSize(1)))
                .andExpect(jsonPath("$.days[0].openTime").value("06:00"))
                .andExpect(jsonPath("$.days[0].courts[0].courtCode").value("A"))
                .andExpect(jsonPath("$.days[0].courts[0].slots", hasSize(16)))
                .andExpect(jsonPath("$.days[0].courts[0].slots[0].startTime").value("06:00"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[0].endTime").value("07:00"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[0].status").value("available"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[0].price").value(120000))
                .andExpect(jsonPath("$.days[0].courts[0].slots[*].status", everyItem(is("available"))));
    }

    @Test
    @DisplayName("O co booking hien cho duyet / da xac nhan kem nguoi dat; o bi khoa hien ly do")
    void bookingsAndBlocks() throws Exception {
        User player = persistUser("player", Role.PLAYER);
        player.setPhone("0911222333");
        userRepository.saveAndFlush(player);
        String pendingId = createBooking(loginAs(player), court, monday, LocalTime.of(9, 0));
        String confirmedId = createBooking(loginAs(player), court, monday, LocalTime.of(18, 0));
        bookingRepository.findById(UUID.fromString(confirmedId)).orElseThrow().getItems()
                .forEach(item -> item.setStatus(BookingItemStatus.CONFIRMED));
        bookingItemRepository.flush();

        CourtBlock block = new CourtBlock();
        block.setId(UUID.randomUUID());
        block.setCourt(court);
        block.setStartTime(monday.atTime(14, 0).atZone(ZONE).toInstant());
        block.setEndTime(monday.atTime(16, 0).atZone(ZONE).toInstant());
        block.setReason("Bao tri luoi");
        block.setStatus(BlockStatus.ACTIVE);
        block.setCreatedBy(owner);
        blockRepository.saveAndFlush(block);

        // 09:00-10:30 phu hai o 9h va 10h; 06:00 la o dau tien nen 9h la o thu 4.
        mockMvc.perform(get(path()).param("date", monday.toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].courts[0].slots[3].status").value("pending"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[3].price").doesNotExist())
                .andExpect(jsonPath("$.days[0].courts[0].slots[3].booking.bookingId").value(pendingId))
                .andExpect(jsonPath("$.days[0].courts[0].slots[3].booking.playerName").value(player.getFullName()))
                .andExpect(jsonPath("$.days[0].courts[0].slots[3].booking.playerPhone").value("0911222333"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[3].booking.status").value("pending_owner_confirmation"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[4].booking.bookingId").value(pendingId))
                .andExpect(jsonPath("$.days[0].courts[0].slots[5].status").value("available"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[8].status").value("blocked"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[8].block.reason").value("Bao tri luoi"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[9].status").value("blocked"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[10].status").value("available"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[12].status").value("confirmed"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[12].booking.bookingId").value(confirmedId));
    }

    @Test
    @DisplayName("Lich tuan: 7 ngay, ngay nghi khong co o; gio dong le tao o cuoi ngan hon")
    void weekSchedule() throws Exception {
        hoursRepository.findAllByVenueIdOrderByDayOfWeekAsc(venue.getId()).forEach(hours -> {
            if (hours.getDayOfWeek() == 7) {
                hours.setClosed(true);
                hours.setOpenTime(null);
                hours.setCloseTime(null);
            }
            if (hours.getDayOfWeek() == 1) {
                hours.setCloseTime(LocalTime.of(22, 30));
            }
        });
        hoursRepository.flush();

        mockMvc.perform(get(path()).param("date", monday.toString()).param("days", "7")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days", hasSize(7)))
                .andExpect(jsonPath("$.days[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.days[0].courts[0].slots", hasSize(17)))
                .andExpect(jsonPath("$.days[0].courts[0].slots[16].startTime").value("22:00"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[16].endTime").value("22:30"))
                .andExpect(jsonPath("$.days[6].closed").value(true))
                .andExpect(jsonPath("$.days[6].courts[0].slots", hasSize(0)));
    }

    @Test
    @DisplayName("San con bao tri hien khong nhan dat; ngay da qua hien da qua")
    void maintenanceAndPast() throws Exception {
        court.setStatus(CourtStatus.MAINTENANCE);
        courtRepository.saveAndFlush(court);
        mockMvc.perform(get(path()).param("date", monday.toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.days[0].courts[0].courtStatus").value("maintenance"))
                .andExpect(jsonPath("$.days[0].courts[0].slots[*].status", everyItem(is("unavailable"))));

        LocalDate yesterday = LocalDate.now(ZONE).minusDays(1);
        mockMvc.perform(get(path()).param("date", yesterday.toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].courts[0].slots[*].status", everyItem(anyOf(is("past"), is("blocked")))));
    }

    @Test
    @DisplayName("Kiem tra tham so va quyen so huu")
    void validation() throws Exception {
        mockMvc.perform(get(path()).param("date", monday.toString()).param("days", "3").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.days").exists());
        mockMvc.perform(get(path()).param("date", LocalDate.now(ZONE).plusDays(90).toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.date").exists());
        mockMvc.perform(get(path()).param("date", "13-11-2026").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.date").exists());

        Venue other = persistVenue(persistOwner("other"), "San Nguoi Khac");
        mockMvc.perform(get("/api/v1/owner/venues/" + other.getId() + "/schedule").param("date", monday.toString())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
    }

    private String path() {
        return "/api/v1/owner/venues/" + venue.getId() + "/schedule";
    }
}
