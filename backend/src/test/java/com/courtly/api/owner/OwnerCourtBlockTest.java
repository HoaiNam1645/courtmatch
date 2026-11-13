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

/** 2.3.10 - Khoa khung gio san. */
class OwnerCourtBlockTest extends OwnerApiTestBase {

    @Autowired private CourtBlockRepository blockRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private com.courtly.domain.venue.CourtPriceRuleRepository priceRuleRepository;

    private User owner;
    private String ownerToken;
    private Venue venue;
    private Court court;
    private LocalDate monday;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-block");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Khoa Gio");
        court = venue.getCourts().getFirst();
        monday = nextMonday();
    }

    @Test
    @DisplayName("Khoa khung gio: khung bien mat khoi lich trong va khong bao gia, ghi audit, hien trong danh sach")
    void blockRemovesSlotFromPlayerAvailability() throws Exception {
        mockMvc.perform(post(path()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(court.getId().toString(), at(14, 0), at(16, 0), "Bao tri luoi")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reason").value("Bao tri luoi"))
                .andExpect(jsonPath("$[0].status").value("active"))
                .andExpect(jsonPath("$[0].createdByName").value(owner.getFullName()));

        mockMvc.perform(get("/api/v1/venues/" + venue.getId() + "/availability").param("date", monday.toString()))
                .andExpect(jsonPath("$.courts[0].slots[8].startTime").value("14:00"))
                .andExpect(jsonPath("$.courts[0].slots[8].status").value("blocked"))
                .andExpect(jsonPath("$.courts[0].slots[10].status").value("available"));

        mockMvc.perform(post("/api/v1/bookings/quote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courtId\":\"%s\",\"startTime\":\"%s\",\"durationMinutes\":60}".formatted(court.getId(), at(15, 0))))
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(get(path()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].courtCode").value("A"));

        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("court_block.create");
            assertThat(log.getNewData()).containsEntry("reason", "Bao tri luoi");
        });
    }

    @Test
    @DisplayName("Khoa nhieu san con cung luc trong mot giao dich; loi o san con nao thi khong san con nao bi khoa")
    void blockSeveralCourtsAtomically() throws Exception {
        Court second = addCourt("B");
        mockMvc.perform(post(path()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(court.getId() + "\",\"" + second.getId(), at(10, 0), at(11, 0), "Su kien noi bo")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)));

        User player = persistUser("player", Role.PLAYER);
        createBooking(loginAs(player), court, monday, LocalTime.of(18, 0));
        mockMvc.perform(post(path()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(court.getId() + "\",\"" + second.getId(), at(18, 0), at(19, 0), "Giai dau")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCK_HAS_BOOKINGS"));
        assertThat(blockRepository.findAll().stream().filter(block -> block.getStatus() == BlockStatus.ACTIVE)
                .filter(block -> block.getCourt().getVenue().getId().equals(venue.getId())).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Khong khoa khung co booking cho duyet / da xac nhan; khong khoa trung khoang da khoa")
    void conflicts() throws Exception {
        User player = persistUser("player", Role.PLAYER);
        String bookingCode = objectMapper.readTree(mockMvc.perform(
                        post("/api/v1/bookings").header("Authorization", bearer(loginAs(player)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"courtId\":\"%s\",\"startTime\":\"%s\",\"durationMinutes\":60}".formatted(court.getId(), at(9, 0))))
                .andReturn().getResponse().getContentAsString()).get("bookingCode").asText();

        mockMvc.perform(post(path()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(court.getId().toString(), at(8, 30), at(9, 30), "Bao tri")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCK_HAS_BOOKINGS"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(bookingCode)));

        mockMvc.perform(post(path()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(court.getId().toString(), at(13, 0), at(15, 0), "Bao tri")))
                .andExpect(status().isCreated());
        mockMvc.perform(post(path()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(court.getId().toString(), at(14, 0), at(16, 0), "Trung")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCK_OVERLAP"));
        // Lien ke nhau khong tinh la trung.
        mockMvc.perform(post(path()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(court.getId().toString(), at(15, 0), at(16, 0), "Lien ke")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Kiem tra du lieu: ly do, thoi gian, qua khu, qua 31 ngay, danh sach san con")
    void validation() throws Exception {
        long before = blockRepository.count();
        expect400(body(court.getId().toString(), at(14, 0), at(16, 0), "  "), "reason");
        expect400(body(court.getId().toString(), at(14, 0), at(16, 0), "ab"), "reason");
        expect400(body(court.getId().toString(), at(16, 0), at(14, 0), "Dao nguoc"), "endTime");
        expect400(body(court.getId().toString(), at(14, 0), at(14, 0), "Bang nhau"), "endTime");
        expect400(body(court.getId().toString(), "2020-01-01T01:00:00Z", "2020-01-01T02:00:00Z", "Qua khu"), "startTime");
        expect400(body(court.getId().toString(), Instant.now().plusSeconds(60).toString(),
                Instant.now().plusSeconds(60L * 60 * 24 * 40).toString(), "Qua dai"), "endTime");
        expect400("{\"courtIds\":[],\"startTime\":\"%s\",\"endTime\":\"%s\",\"reason\":\"Rong\"}".formatted(at(10, 0), at(11, 0)), "courtIds");
        expect400(body(UUID.randomUUID().toString(), at(10, 0), at(11, 0), "San con la"), "courtIds");
        expect400(body(court.getId() + "\",\"" + court.getId(), at(10, 0), at(11, 0), "Trung"), "courtIds");
        expect400("{\"courtIds\":[\"khong-phai-uuid\"],\"startTime\":\"%s\",\"endTime\":\"%s\",\"reason\":\"Sai\"}".formatted(at(10, 0), at(11, 0)), "courtIds[0]");
        expect400("{\"courtIds\":[\"%s\"],\"startTime\":\"mai\",\"endTime\":\"%s\",\"reason\":\"Sai\"}".formatted(court.getId(), at(11, 0)), "startTime");
        assertThat(blockRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("San con cua san khac, san cua chu san khac, nguoi choi deu bi tu choi")
    void ownership() throws Exception {
        Venue other = persistVenue(persistOwner("other"), "San Nguoi Khac");
        Court otherCourt = other.getCourts().getFirst();
        mockMvc.perform(post(path()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(otherCourt.getId().toString(), at(10, 0), at(11, 0), "Khoa lan")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.courtIds").exists());
        mockMvc.perform(post("/api/v1/owner/venues/" + other.getId() + "/blocks").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(otherCourt.getId().toString(), at(10, 0), at(11, 0), "Khoa lan")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(path()).param("size", "0").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        User player = persistUser("player", Role.PLAYER);
        mockMvc.perform(post(path()).header("Authorization", bearer(loginAs(player)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(court.getId().toString(), at(10, 0), at(11, 0), "Nguoi choi")))
                .andExpect(status().isForbidden());
    }

    private Court addCourt(String code) {
        Court extra = new Court();
        extra.setId(UUID.randomUUID());
        extra.setVenue(venue);
        extra.setName("San " + code);
        extra.setCourtCode(code);
        venue.getCourts().add(extra);
        venueRepository.saveAndFlush(venue);
        com.courtly.domain.venue.CourtPriceRule rule = priceRuleRepository.saveAndFlush(priceRuleOf(court, extra));
        extra.getPriceRules().add(rule);
        return extra;
    }

    private static com.courtly.domain.venue.CourtPriceRule priceRuleOf(Court from, Court to) {
        com.courtly.domain.venue.CourtPriceRule source = from.getPriceRules().getFirst();
        com.courtly.domain.venue.CourtPriceRule rule = new com.courtly.domain.venue.CourtPriceRule();
        rule.setId(UUID.randomUUID());
        rule.setCourt(to);
        rule.setStartTime(source.getStartTime());
        rule.setEndTime(source.getEndTime());
        rule.setPricePerHour(source.getPricePerHour());
        rule.setEffectiveFrom(source.getEffectiveFrom());
        return rule;
    }

    private void expect400(String body, String field) throws Exception {
        mockMvc.perform(post(path()).header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['" + field + "']").exists());
    }

    private String at(int hour, int minute) {
        return monday.atTime(hour, minute).atZone(ZONE).toInstant().toString();
    }

    private static String body(String courtIds, String start, String end, String reason) {
        return "{\"courtIds\":[\"%s\"],\"startTime\":\"%s\",\"endTime\":\"%s\",\"reason\":\"%s\"}".formatted(courtIds, start, end, reason);
    }

    private String path() {
        return "/api/v1/owner/venues/" + venue.getId() + "/blocks";
    }
}
