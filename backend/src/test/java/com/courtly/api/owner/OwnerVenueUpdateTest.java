package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.CourtStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtRepository;
import com.courtly.domain.venue.Venue;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.4 - Cap nhat thong tin san. */
class OwnerVenueUpdateTest extends OwnerApiTestBase {

    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private CourtRepository courtRepository;

    private User owner;
    private String ownerToken;
    private Venue venue;
    private Court court;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-update");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Cap Nhat");
        court = venue.getCourts().getFirst();
    }

    @Test
    @DisplayName("Xem chi tiet san cua minh; san cua chu san khac, id khong ton tai deu 404; id sai dinh dang 400")
    void getDetail() throws Exception {
        mockMvc.perform(get(venuePath()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("San Cap Nhat"))
                .andExpect(jsonPath("$.approvalStatus").value("approved"))
                .andExpect(jsonPath("$.courts", hasSize(1)))
                .andExpect(jsonPath("$.courts[0].status").value("active"));

        Venue other = persistVenue(persistOwner("other"), "San Nguoi Khac");
        mockMvc.perform(get("/api/v1/owner/venues/" + other.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/owner/venues/" + UUID.randomUUID()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/owner/venues/khong-phai-uuid").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Cap nhat thong tin va tien ich: thay danh sach tien ich, giu trang thai duyet, ghi audit")
    void updateInfoAndServices() throws Exception {
        mockMvc.perform(put(venuePath())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(infoBody("San Cap Nhat Moi", "[{\"code\":\"parking\",\"price\":5000,\"note\":\"Xe may\"},"
                                + "{\"code\":\"shower\",\"price\":null,\"note\":null}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("San Cap Nhat Moi"))
                .andExpect(jsonPath("$.phone").value("0905123456"))
                .andExpect(jsonPath("$.services", hasSize(2)));

        // Lan hai: bo phong tam, doi gia bai xe, them nuoc uong - cung mot lan luu.
        mockMvc.perform(put(venuePath())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(infoBody("San Cap Nhat Moi", "[{\"code\":\"parking\",\"price\":0,\"note\":\"Mien phi\"},"
                                + "{\"code\":\"drinking_water\",\"price\":10000,\"note\":null}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services", hasSize(2)))
                .andExpect(jsonPath("$.services[?(@.code=='parking')].price").value(0))
                .andExpect(jsonPath("$.services[?(@.code=='parking')].note").value("Mien phi"))
                .andExpect(jsonPath("$.services[?(@.code=='shower')]").isEmpty())
                .andExpect(jsonPath("$.services[?(@.code=='drinking_water')]").isNotEmpty())
                .andExpect(jsonPath("$.approvalStatus").value("approved"));

        Venue saved = venueRepository.findById(venue.getId()).orElseThrow();
        assertThat(saved.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("venue.update");
            assertThat(log.getEntityId()).isEqualTo(venue.getId());
            assertThat(log.getOldData()).containsEntry("name", "San Cap Nhat");
            assertThat(log.getNewData()).containsEntry("name", "San Cap Nhat Moi");
        });

        // Nguoi choi thay ten moi ngay vi san van dang duoc duyet.
        mockMvc.perform(get("/api/v1/venues/" + venue.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("San Cap Nhat Moi"));
    }

    @Test
    @DisplayName("Cap nhat thong tin: kiem tra nhu khi them san, khong sua duoc san cua nguoi khac")
    void updateValidation() throws Exception {
        mockMvc.perform(put(venuePath())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(infoBody("Sa", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());

        mockMvc.perform(put(venuePath())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(infoBody("San Sai Toa Do", "[]").replace("16.0609", "10.7769")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.latitude").exists());

        Venue other = persistVenue(persistOwner("other"), "San Nguoi Khac");
        mockMvc.perform(put("/api/v1/owner/venues/" + other.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(infoBody("Chiem San", "[]")))
                .andExpect(status().isNotFound());
        assertThat(venueRepository.findById(other.getId()).orElseThrow().getName()).isEqualTo("San Nguoi Khac");
    }

    @Test
    @DisplayName("Them san con: ma in hoa, trung ma (khong phan biet hoa thuong) bi 409, loai san la bi 400")
    void addCourt() throws Exception {
        mockMvc.perform(post(venuePath() + "/courts")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"San B","courtCode":"b","courtType":"standard","surfaceType":"vinyl","indoor":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.courtCode").value("B"))
                .andExpect(jsonPath("$.indoor").value(false))
                .andExpect(jsonPath("$.status").value("active"));

        mockMvc.perform(post(venuePath() + "/courts")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"San A2","courtCode":"a","courtType":"standard","indoor":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.courtCode").exists());

        mockMvc.perform(post(venuePath() + "/courts")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"San C","courtCode":"C","courtType":"luxury","indoor":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.courtType").exists());

        mockMvc.perform(get(venuePath()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.courts", hasSize(2)));
    }

    @Test
    @DisplayName("Sua san con; khong cho bao tri khi con booking chua choi; san bao tri khong nhan dat moi")
    void updateCourtStatus() throws Exception {
        User player = persistUser("player", Role.PLAYER);
        String playerToken = loginAs(player);
        createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));

        mockMvc.perform(put(courtPath(court))
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courtBody("San A Moi", "A", "maintenance")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURT_HAS_UPCOMING_BOOKINGS"))
                .andExpect(jsonPath("$.fieldErrors.status").exists());
        assertThat(courtRepository.findById(court.getId()).orElseThrow().getStatus()).isEqualTo(CourtStatus.ACTIVE);

        // Doi ten / loai san van duoc khi giu trang thai hoat dong.
        mockMvc.perform(put(courtPath(court))
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courtBody("San A Moi", "A1", "active")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("San A Moi"))
                .andExpect(jsonPath("$.courtCode").value("A1"))
                .andExpect(jsonPath("$.courtType").value("vip"));

        // San con moi chua co booking thi bao tri duoc, va nguoi choi khong dat duoc nua.
        String added = mockMvc.perform(post(venuePath() + "/courts")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"San B","courtCode":"B","courtType":"standard","indoor":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Court courtB = courtRepository.findById(UUID.fromString(objectMapper.readTree(added).get("id").asText())).orElseThrow();
        mockMvc.perform(put(courtPath(courtB))
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courtBody("San B", "B", "maintenance")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("maintenance"));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courtId":"%s","startTime":"%s","durationMinutes":60,"note":null}
                                """.formatted(courtB.getId(), nextMonday().atTime(10, 0).atZone(ZONE).toInstant())))
                .andExpect(status().isNotFound());

        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("court.update");
            assertThat(log.getNewData()).containsEntry("status", "maintenance");
        });
    }

    @Test
    @DisplayName("Sua san con: trung ma san con khac, san con cua san khac, trang thai la")
    void updateCourtValidation() throws Exception {
        mockMvc.perform(post(venuePath() + "/courts")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"San B","courtCode":"B","courtType":"standard","indoor":true}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(put(courtPath(court))
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courtBody("San A", "b", "active")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.courtCode").exists());

        mockMvc.perform(put(courtPath(court))
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courtBody("San A", "A", "closed")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());

        Venue other = persistVenue(persistOwner("other"), "San Nguoi Khac");
        Court otherCourt = other.getCourts().getFirst();
        mockMvc.perform(put("/api/v1/owner/venues/" + venue.getId() + "/courts/" + otherCourt.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courtBody("San A", "A", "active")))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/owner/venues/" + other.getId() + "/courts/" + otherCourt.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(courtBody("San A", "A", "inactive")))
                .andExpect(status().isNotFound());
        assertThat(courtRepository.findById(otherCourt.getId()).orElseThrow().getStatus()).isEqualTo(CourtStatus.ACTIVE);
    }

    private String venuePath() {
        return "/api/v1/owner/venues/" + venue.getId();
    }

    private String courtPath(Court target) {
        return venuePath() + "/courts/" + target.getId();
    }

    private static String courtBody(String name, String code, String status) {
        return """
                {"name":"%s","courtCode":"%s","courtType":"vip","surfaceType":"wood","indoor":true,"status":"%s"}
                """.formatted(name, code, status);
    }

    private static String infoBody(String name, String services) {
        return """
                {"name":"%s","description":"Mo ta moi","address":"20 Bach Dang","ward":"Hai Chau 1",
                 "district":"Hải Châu","latitude":16.0609,"longitude":108.2237,
                 "phone":"0905 123 456","email":"","services":%s}
                """.formatted(name, services);
    }
}
