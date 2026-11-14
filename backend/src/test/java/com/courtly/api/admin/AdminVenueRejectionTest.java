package com.courtly.api.admin;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.venue.Venue;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 2.3.35 - Tu choi san cau long. */
class AdminVenueRejectionTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/venues";

    private User admin;
    private String adminToken;
    private User owner;
    private String ownerToken;
    private Venue venue;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-reject");
        adminToken = loginAs(admin);
        owner = persistOwner("owner-reject");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Bi Tu Choi");
        venue.setApprovalStatus(ApprovalStatus.PENDING);
        venue.setStatus(VenueStatus.PENDING);
        venueRepository.saveAndFlush(venue);
    }

    private ResultActions reject(String token, UUID id, String body) throws Exception {
        return mockMvc.perform(post(PATH + "/" + id + "/reject").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("Danh muc ly do lay tu API, ly do khac bat buoc ghi chu")
    void reasons() throws Exception {
        mockMvc.perform(get(PATH + "/reject-reasons").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasons[*].code", hasItem("invalid_location")))
                .andExpect(jsonPath("$.reasons[?(@.code=='other')].noteRequired").value(true))
                .andExpect(jsonPath("$.reasons[?(@.code=='incomplete_info')].noteRequired").value(false));
    }

    @Test
    @DisplayName("Tu choi kem ly do: san sang rejected, khong hien voi nguoi choi, chu san thay ly do, ghi nguoi tu choi")
    void rejectVenue() throws Exception {
        reject(adminToken, venue.getId(), "{\"reasonCode\":\"invalid_location\",\"note\":\"Toa do nam o ngoai khu vuc\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue.approvalStatus").value("rejected"))
                .andExpect(jsonPath("$.venue.status").value("rejected"))
                .andExpect(jsonPath("$.rejection.code").value("invalid_location"))
                .andExpect(jsonPath("$.rejection.reason").value("Dia chi hoac toa do khong chinh xac: Toa do nam o ngoai khu vuc"))
                .andExpect(jsonPath("$.rejection.rejectedBy").value(admin.getFullName()))
                .andExpect(jsonPath("$.canApprove").value(false));
        mockMvc.perform(get("/api/v1/venues/" + venue.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/owner/venues/" + venue.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("rejected"))
                .andExpect(jsonPath("$.rejectionReason").value("Dia chi hoac toa do khong chinh xac: Toa do nam o ngoai khu vuc"))
                .andExpect(jsonPath("$.rejectedAt").exists());
        reject(adminToken, venue.getId(), "{\"reasonCode\":\"incomplete_info\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VENUE_REVIEW_INVALID"));
    }

    @Test
    @DisplayName("Chu san sua roi gui duyet lai: san quay ve cho duyet; chi gui lai duoc san bi tu choi; san cua chu khac 404")
    void resubmit() throws Exception {
        mockMvc.perform(post("/api/v1/owner/venues/" + venue.getId() + "/resubmit").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VENUE_REVIEW_INVALID"));
        reject(adminToken, venue.getId(), "{\"reasonCode\":\"incomplete_info\"}").andExpect(status().isOk());

        String other = loginAs(persistOwner("other-owner"));
        mockMvc.perform(post("/api/v1/owner/venues/" + venue.getId() + "/resubmit").header("Authorization", bearer(other)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/owner/venues/" + venue.getId() + "/resubmit").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("pending"))
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
        mockMvc.perform(get(PATH + "/" + venue.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.canApprove").value(true));
        // Duyet lan nay thanh cong va xoa dau vet tu choi.
        mockMvc.perform(post(PATH + "/" + venue.getId() + "/approve").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rejection").doesNotExist());
    }

    @Test
    @DisplayName("Ly do khong hop le / thieu ghi chu khi chon 'khac' / ghi chu qua dai bi tu choi (400)")
    void validation() throws Exception {
        reject(adminToken, venue.getId(), "{}").andExpect(status().isBadRequest());
        reject(adminToken, venue.getId(), "{\"reasonCode\":\"khong_co\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reasonCode").exists());
        reject(adminToken, venue.getId(), "{\"reasonCode\":\"other\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.note").exists());
        reject(adminToken, venue.getId(), "{\"reasonCode\":\"other\",\"note\":\"ab\"}").andExpect(status().isBadRequest());
        reject(adminToken, venue.getId(), "{\"reasonCode\":\"incomplete_info\",\"note\":\"" + "x".repeat(501) + "\"}").andExpect(status().isBadRequest());
        reject(adminToken, venue.getId(), "{\"reasonCode\":\"other\",\"note\":\"Khong dung chu de\"}").andExpect(status().isOk());
        reject(adminToken, UUID.randomUUID(), "{\"reasonCode\":\"incomplete_info\"}").andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "/abc/reject").header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCode\":\"incomplete_info\"}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("San dang co booking dang xu ly thi khong tu choi duoc; san da duyet khong tu choi duoc")
    void blockedByBookings() throws Exception {
        venue.setApprovalStatus(ApprovalStatus.APPROVED);
        venue.setStatus(VenueStatus.ACTIVE);
        venueRepository.saveAndFlush(venue);
        createBooking(loginAs(persistUser("player-reject", Role.PLAYER)), venue.getCourts().getFirst(), nextMonday(), LocalTime.of(9, 0));
        reject(adminToken, venue.getId(), "{\"reasonCode\":\"incomplete_info\"}").andExpect(status().isConflict());

        venue.setApprovalStatus(ApprovalStatus.PENDING);
        venue.setStatus(VenueStatus.PENDING);
        venueRepository.saveAndFlush(venue);
        reject(adminToken, venue.getId(), "{\"reasonCode\":\"incomplete_info\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.bookings").exists());
        mockMvc.perform(get(PATH + "/" + venue.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.venue.approvalStatus").value("pending")).andExpect(jsonPath("$.rejection").doesNotExist());
    }

    @Test
    @DisplayName("Can quyen venue.reject; chu san va nguoi choi 403; chua dang nhap 401")
    void accessControl() throws Exception {
        reject(ownerToken, venue.getId(), "{\"reasonCode\":\"incomplete_info\"}").andExpect(status().isForbidden());
        reject(loginAs(persistUser("p", Role.PLAYER)), venue.getId(), "{\"reasonCode\":\"incomplete_info\"}").andExpect(status().isForbidden());
        mockMvc.perform(post(PATH + "/" + venue.getId() + "/reject").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH + "/reject-reasons")).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "venue.reject".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        reject(adminToken, venue.getId(), "{\"reasonCode\":\"incomplete_info\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(get(PATH + "/reject-reasons").header("Authorization", bearer(adminToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH + "/" + venue.getId()).header("Authorization", bearer(adminToken))).andExpect(status().isOk());
    }
}
