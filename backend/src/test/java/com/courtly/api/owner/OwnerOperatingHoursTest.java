package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueOperatingHoursRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.7 - Quan ly gio hoat dong cua san. */
class OwnerOperatingHoursTest extends OwnerApiTestBase {

    @Autowired private VenueOperatingHoursRepository hoursRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private String ownerToken;
    private Venue venue;
    private Court court;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        User owner = persistOwner("owner-hours");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Gio Hoat Dong");
        court = venue.getCourts().getFirst();
    }

    @Test
    @DisplayName("Xem gio hoat dong du 7 ngay")
    void getHours() throws Exception {
        mockMvc.perform(get(path()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days", hasSize(7)))
                .andExpect(jsonPath("$.days[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.days[0].openTime").value("06:00"))
                .andExpect(jsonPath("$.days[0].closeTime").value("22:00"))
                .andExpect(jsonPath("$.days[0].configured").value(true))
                .andExpect(jsonPath("$.conflicts", hasSize(0)));
    }

    @Test
    @DisplayName("Ngay chua khai bao hien la nghi; luu du 7 ngay thi them ban ghi con thieu")
    void missingDaysAreCreated() throws Exception {
        hoursRepository.deleteAll(hoursRepository.findAllByVenueIdOrderByDayOfWeekAsc(venue.getId()).stream()
                .filter(hours -> hours.getDayOfWeek() == 7).toList());
        venue.getOperatingHours().removeIf(hours -> hours.getDayOfWeek() == 7);
        hoursRepository.flush();

        mockMvc.perform(get(path()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.days[6].configured").value(false))
                .andExpect(jsonPath("$.days[6].closed").value(true));

        mockMvc.perform(put(path())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(week("07:00", "21:00", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[6].configured").value(true))
                .andExpect(jsonPath("$.days[6].openTime").value("07:00"));
        assertThat(hoursRepository.findAllByVenueIdOrderByDayOfWeekAsc(venue.getId())).hasSize(7);
    }

    @Test
    @DisplayName("Doi gio va nghi Chu nhat: lich trong doc gio moi, ghi audit")
    void updateHoursChangesAvailability() throws Exception {
        mockMvc.perform(put(path())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(week("08:00", "20:00", 7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].openTime").value("08:00"))
                .andExpect(jsonPath("$.days[6].closed").value(true))
                .andExpect(jsonPath("$.days[6].openTime").doesNotExist());

        LocalDate monday = nextMonday();
        mockMvc.perform(get("/api/v1/venues/" + venue.getId() + "/availability").param("date", monday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openTime").value("08:00"))
                .andExpect(jsonPath("$.closeTime").value("20:00"));
        mockMvc.perform(get("/api/v1/venues/" + venue.getId() + "/availability").param("date", monday.plusDays(6).toString()))
                .andExpect(jsonPath("$.closed").value(true));

        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("operating_hours.update");
            assertThat(log.getEntityId()).isEqualTo(venue.getId());
        });
    }

    @Test
    @DisplayName("Booking chua choi nam ngoai gio moi duoc liet ke canh bao, khong bi huy")
    void conflictsAreReportedNotCancelled() throws Exception {
        User player = persistUser("player", Role.PLAYER);
        String early = createBooking(loginAs(player), court, nextMonday(), LocalTime.of(6, 30));
        createBooking(loginAs(player), court, nextMonday(), LocalTime.of(12, 0));

        mockMvc.perform(put(path())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(week("08:00", "22:00", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicts", hasSize(1)))
                .andExpect(jsonPath("$.conflicts[0].bookingId").value(early))
                .andExpect(jsonPath("$.conflicts[0].courtName").value("San A"))
                .andExpect(jsonPath("$.conflicts[0].status").value("pending_owner_confirmation"));

        assertThat(bookingRepository.findById(UUID.fromString(early)).orElseThrow().getStatus().getValue())
                .isEqualTo("pending_owner_confirmation");
        // Xem lai van thay canh bao.
        mockMvc.perform(get(path()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.conflicts", hasSize(1)));

        // Nghi han Thu hai thi ca hai booking deu nam ngoai gio mo cua.
        mockMvc.perform(put(path())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(week("08:00", "22:00", 1)))
                .andExpect(jsonPath("$.conflicts", hasSize(2)));
    }

    @Test
    @DisplayName("Kiem tra du lieu: thieu ngay, trung ngay, dong truoc mo, thieu gio, gio sai dinh dang")
    void validation() throws Exception {
        String sixDays = IntStream.rangeClosed(1, 6)
                .mapToObj(day -> "{\"dayOfWeek\":%d,\"closed\":false,\"openTime\":\"06:00\",\"closeTime\":\"22:00\"}".formatted(day))
                .collect(Collectors.joining(",", "{\"days\":[", "]}"));
        expectBadRequest(sixDays, "days");
        expectBadRequest(week("06:00", "22:00", null).replace("\"dayOfWeek\":7", "\"dayOfWeek\":1"), "days[6].dayOfWeek");
        expectBadRequest(week("22:00", "06:00", null), "days[0].closeTime");
        expectBadRequest(week("06:00", "22:00", null).replaceFirst("\"openTime\":\"06:00\"", "\"openTime\":null"), "days[0].openTime");
        expectBadRequest(week("06:00", "22:00", null).replaceFirst("\"06:00\"", "\"6h\""), "days[0].openTime");
        expectBadRequest(week("06:00", "22:00", null).replaceFirst("\"dayOfWeek\":1", "\"dayOfWeek\":9"), "days[0].dayOfWeek");

        // Gio cu van nguyen.
        assertThat(hoursRepository.findAllByVenueIdOrderByDayOfWeekAsc(venue.getId()).getFirst().getOpenTime())
                .isEqualTo(LocalTime.of(6, 0));
    }

    @Test
    @DisplayName("San cua chu san khac tra 404")
    void ownership() throws Exception {
        Venue other = persistVenue(persistOwner("other"), "San Nguoi Khac");
        mockMvc.perform(put("/api/v1/owner/venues/" + other.getId() + "/operating-hours")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(week("08:00", "20:00", null)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(path()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    private void expectBadRequest(String body, String field) throws Exception {
        mockMvc.perform(put(path())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['" + field + "']").exists());
    }

    private String path() {
        return "/api/v1/owner/venues/" + venue.getId() + "/operating-hours";
    }

    /** Ca tuan cung gio; closedDay (neu co) nghi ca ngay. */
    private static String week(String open, String close, Integer closedDay) {
        return IntStream.rangeClosed(1, 7)
                .mapToObj(day -> closedDay != null && closedDay == day
                        ? "{\"dayOfWeek\":%d,\"closed\":true}".formatted(day)
                        : "{\"dayOfWeek\":%d,\"closed\":false,\"openTime\":\"%s\",\"closeTime\":\"%s\"}".formatted(day, open, close))
                .collect(Collectors.joining(",", "{\"days\":[", "]}"));
    }
}
