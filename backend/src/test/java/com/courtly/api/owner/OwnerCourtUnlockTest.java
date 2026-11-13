package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BlockStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtBlock;
import com.courtly.domain.venue.CourtBlockRepository;
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

/** 2.3.11 - Mo khoa khung gio san. */
class OwnerCourtUnlockTest extends OwnerApiTestBase {

    @Autowired private CourtBlockRepository blockRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private User owner;
    private String ownerToken;
    private Venue venue;
    private Court court;
    private LocalDate monday;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-unlock");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Mo Khoa");
        court = venue.getCourts().getFirst();
        monday = nextMonday();
    }

    @Test
    @DisplayName("Mo khoa: ban ghi chuyen cancelled, khung gio dat duoc lai, khong con trong danh sach, ghi audit")
    void unlockRestoresSlots() throws Exception {
        CourtBlock block = persistBlock(court, 14, 16, "Bao tri luoi");

        mockMvc.perform(post(path(block)).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.id").value(block.getId().toString()));

        assertThat(blockRepository.findById(block.getId()).orElseThrow().getStatus()).isEqualTo(BlockStatus.CANCELLED);
        mockMvc.perform(get("/api/v1/venues/" + venue.getId() + "/availability").param("date", monday.toString()))
                .andExpect(jsonPath("$.courts[0].slots[8].status").value("available"));
        mockMvc.perform(post("/api/v1/bookings/quote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courtId\":\"%s\",\"startTime\":\"%s\",\"durationMinutes\":60}".formatted(court.getId(), at(14, 0))))
                .andExpect(jsonPath("$.available").value(true));
        mockMvc.perform(get("/api/v1/owner/venues/" + venue.getId() + "/blocks").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content[?(@.id=='" + block.getId() + "')]", hasSize(0)));

        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("court_block.cancel");
            assertThat(log.getEntityId()).isEqualTo(block.getId());
            assertThat(log.getOldData()).containsEntry("status", "active");
            assertThat(log.getNewData()).containsEntry("status", "cancelled");
        });
    }

    @Test
    @DisplayName("Sau khi mo khoa, nguoi choi dat duoc khung gio vua mo")
    void playerCanBookAfterUnlock() throws Exception {
        CourtBlock block = persistBlock(court, 10, 12, "Su kien");
        User player = persistUser("player", Role.PLAYER);
        String token = loginAs(player);
        mockMvc.perform(post("/api/v1/bookings").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courtId\":\"%s\",\"startTime\":\"%s\",\"durationMinutes\":60}".formatted(court.getId(), at(10, 0))))
                .andExpect(status().isConflict());

        mockMvc.perform(post(path(block)).header("Authorization", bearer(ownerToken))).andExpect(status().isOk());
        createBooking(token, court, monday, LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("Mo khoa lan hai, mo khoa khoang da ket thuc: 409; id la: 404; id sai dinh dang: 400")
    void invalidStates() throws Exception {
        CourtBlock block = persistBlock(court, 14, 16, "Bao tri");
        mockMvc.perform(post(path(block)).header("Authorization", bearer(ownerToken))).andExpect(status().isOk());
        mockMvc.perform(post(path(block)).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCK_STATE_INVALID"));

        CourtBlock ended = new CourtBlock();
        ended.setId(UUID.randomUUID());
        ended.setCourt(court);
        ended.setStartTime(Instant.now().minusSeconds(7200));
        ended.setEndTime(Instant.now().minusSeconds(3600));
        ended.setReason("Da qua");
        ended.setStatus(BlockStatus.ACTIVE);
        blockRepository.saveAndFlush(ended);
        mockMvc.perform(post(path(ended)).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCK_STATE_INVALID"));

        mockMvc.perform(post("/api/v1/owner/venues/" + venue.getId() + "/blocks/" + UUID.randomUUID() + "/unlock")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/owner/venues/" + venue.getId() + "/blocks/khong-phai-uuid/unlock")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Khong mo khoa duoc khoang khoa cua san / chu san khac; nguoi choi bi chan")
    void ownership() throws Exception {
        Venue other = persistVenue(persistOwner("other"), "San Nguoi Khac");
        CourtBlock otherBlock = persistBlock(other.getCourts().getFirst(), 14, 16, "Cua nguoi khac");

        mockMvc.perform(post("/api/v1/owner/venues/" + other.getId() + "/blocks/" + otherBlock.getId() + "/unlock")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        // Khoang khoa cua san khac nhung gan vao duong dan san cua minh.
        mockMvc.perform(post("/api/v1/owner/venues/" + venue.getId() + "/blocks/" + otherBlock.getId() + "/unlock")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        assertThat(blockRepository.findById(otherBlock.getId()).orElseThrow().getStatus()).isEqualTo(BlockStatus.ACTIVE);

        CourtBlock mine = persistBlock(court, 14, 16, "Cua toi");
        mockMvc.perform(post(path(mine)).header("Authorization", bearer(loginAs(persistUser("player", Role.PLAYER)))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path(mine))).andExpect(status().isUnauthorized());
    }

    private CourtBlock persistBlock(Court target, int startHour, int endHour, String reason) {
        CourtBlock block = new CourtBlock();
        block.setId(UUID.randomUUID());
        block.setCourt(target);
        block.setStartTime(monday.atTime(startHour, 0).atZone(ZONE).toInstant());
        block.setEndTime(monday.atTime(endHour, 0).atZone(ZONE).toInstant());
        block.setReason(reason);
        block.setStatus(BlockStatus.ACTIVE);
        block.setCreatedBy(owner);
        return blockRepository.saveAndFlush(block);
    }

    private String at(int hour, int minute) {
        return monday.atTime(hour, minute).atZone(ZONE).toInstant().toString();
    }

    private String path(CourtBlock block) {
        return "/api/v1/owner/venues/" + venue.getId() + "/blocks/" + block.getId() + "/unlock";
    }
}
