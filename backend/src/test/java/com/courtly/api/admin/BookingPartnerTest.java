package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingItemStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.match.MatchRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** B5 - dat san tu ket qua ghep cap: nho nguoi choi cung va goi y khi ghi tran dau. */
class BookingPartnerTest extends AdminApiTestBase {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private MatchRepository matchRepository;

    private Court court;
    private User player;
    private String playerToken;
    private User partner;
    private int dayOffset;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        User owner = persistOwner("owner-partner");
        Venue venue = persistVenue(owner, "San Cap Doi");
        court = venue.getCourts().getFirst();
        player = persistUser("player-partner", Role.PLAYER);
        playerToken = loginAs(player);
        partner = persistUser("doi-tac", Role.PLAYER);
    }

    private org.springframework.test.web.servlet.ResultActions book(String partnerJson) throws Exception {
        String start = nextMonday().plusDays(dayOffset++).atTime(LocalTime.of(9, 0)).atZone(ZONE).toInstant().toString();
        return mockMvc.perform(post("/api/v1/bookings").header("Authorization", bearer(playerToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"courtId\":\"%s\",\"startTime\":\"%s\",\"durationMinutes\":90%s}".formatted(court.getId(), start, partnerJson)));
    }

    private static String partnerOf(UUID id) {
        return ",\"partnerUserId\":\"" + id + "\"";
    }

    @Test
    @DisplayName("Dat san cung nguoi choi khac: luu va tra ve nguoi choi cung o chi tiet booking; khong chon thi khong co")
    void storesPartner() throws Exception {
        String id = objectMapper.readTree(book(partnerOf(partner.getId())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.partner.id").value(partner.getId().toString()))
                .andExpect(jsonPath("$.partner.fullName").value(partner.getFullName()))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(get("/api/v1/bookings/" + id).header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.partner.fullName").value(partner.getFullName()));
        assertThat(bookingRepository.findById(UUID.fromString(id)).orElseThrow().getPartner().getId()).isEqualTo(partner.getId());
        book("").andExpect(status().isCreated()).andExpect(jsonPath("$.partner").doesNotExist());
        book(",\"partnerUserId\":null").andExpect(status().isCreated()).andExpect(jsonPath("$.partner").doesNotExist());
    }

    @Test
    @DisplayName("Nguoi choi cung khong hop le bi tu choi 400 va khong tao booking: chinh minh, khong ton tai, khong phai nguoi choi, bi khoa, tam ngung")
    void rejectsInvalidPartner() throws Exception {
        long before = bookingRepository.count();
        book(partnerOf(player.getId())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.partnerUserId").exists());
        book(partnerOf(UUID.randomUUID())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.partnerUserId").exists());
        book(partnerOf(persistOwner("chu-san-khac").getId())).andExpect(status().isBadRequest());
        book(partnerOf(persistAdmin("quan-tri").getId())).andExpect(status().isBadRequest());
        User banned = persistUser("bi-khoa", Role.PLAYER);
        banned.setStatus(UserStatus.BANNED);
        userRepository.saveAndFlush(banned);
        book(partnerOf(banned.getId())).andExpect(status().isBadRequest());
        User inactive = persistUser("tam-ngung", Role.PLAYER);
        inactive.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(inactive);
        book(partnerOf(inactive.getId())).andExpect(status().isBadRequest());
        book(",\"partnerUserId\":\"khong-phai-uuid\"").andExpect(status().isBadRequest());
        assertThat(bookingRepository.count()).isEqualTo(before);
    }

    private UUID completedBookingWithPartner() throws Exception {
        UUID id = UUID.fromString(objectMapper.readTree(book(partnerOf(partner.getId())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText());
        Booking booking = bookingRepository.findById(id).orElseThrow();
        Instant played = Instant.now().minus(3, ChronoUnit.HOURS);
        booking.getItems().forEach(item -> {
            item.setStartTime(played);
            item.setEndTime(played.plus(90, ChronoUnit.MINUTES));
            item.setStatus(BookingItemStatus.COMPLETED);
        });
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(Instant.now().minus(1, ChronoUnit.HOURS));
        bookingRepository.saveAndFlush(booking);
        return id;
    }

    @Test
    @DisplayName("Booking da choi xong kem nguoi choi cung: goi y ghi tran co san doi thu; tao tran tu booking gan matches.booking_id va het goi y")
    void suggestsPartnerAndLinksMatchToBooking() throws Exception {
        UUID bookingId = completedBookingWithPartner();
        JsonNode suggestions = objectMapper.readTree(mockMvc.perform(get("/api/v1/matches/suggestions").header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode mine = null;
        for (JsonNode item : suggestions.get("content")) {
            if (bookingId.toString().equals(item.get("bookingId").asText())) {
                mine = item;
            }
        }
        assertThat(mine).isNotNull();
        assertThat(mine.get("partnerUserId").asText()).isEqualTo(partner.getId().toString());
        assertThat(mine.get("partnerName").asText()).isEqualTo(partner.getFullName());

        String matchJson = "{\"bookingId\":\"%s\",\"matchType\":\"single\",\"team1UserIds\":[\"%s\"],\"team2UserIds\":[\"%s\"]}"
                .formatted(bookingId, player.getId(), mine.get("partnerUserId").asText());
        String matchId = objectMapper.readTree(mockMvc.perform(post("/api/v1/matches").header("Authorization", bearer(playerToken))
                .contentType(MediaType.APPLICATION_JSON).content(matchJson)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.dataSource").value("booking"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        assertThat(matchRepository.findById(UUID.fromString(matchId)).orElseThrow().getBooking().getId()).isEqualTo(bookingId);

        JsonNode after = objectMapper.readTree(mockMvc.perform(get("/api/v1/matches/suggestions").header("Authorization", bearer(playerToken)))
                .andReturn().getResponse().getContentAsString());
        for (JsonNode item : after.get("content")) {
            assertThat(item.get("bookingId").asText()).isNotEqualTo(bookingId.toString());
        }
    }

    @Test
    @DisplayName("Booking khong co nguoi choi cung: goi y ghi tran khong co doi thu mac dinh; chua choi xong thi chua duoc goi y")
    void suggestionWithoutPartner() throws Exception {
        UUID bookingId = UUID.fromString(objectMapper.readTree(book("").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText());
        mockMvc.perform(get("/api/v1/matches/suggestions").header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
        Booking booking = bookingRepository.findById(bookingId).orElseThrow();
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setCompletedAt(Instant.now());
        bookingRepository.saveAndFlush(booking);
        mockMvc.perform(get("/api/v1/matches/suggestions").header("Authorization", bearer(playerToken)))
                .andExpect(jsonPath("$.content[0].bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.content[0].partnerUserId").doesNotExist())
                .andExpect(jsonPath("$.content[0].partnerName").doesNotExist());
    }
}
