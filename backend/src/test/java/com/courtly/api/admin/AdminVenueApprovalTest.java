package com.courtly.api.admin;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.CourtStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueOperatingHoursRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 2.3.34 - Phe duyet san cau long. */
class AdminVenueApprovalTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/venues";

    @Autowired private CourtRepository courtRepository;
    @Autowired private VenueOperatingHoursRepository hoursRepository;

    private User admin;
    private String adminToken;
    private User owner;
    private Venue venue;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-venues");
        adminToken = loginAs(admin);
        owner = persistOwner("owner-venues");
        venue = persistVenue(owner, "San Cho Duyet Moi");
        venue.setApprovalStatus(ApprovalStatus.PENDING);
        venue.setStatus(VenueStatus.PENDING);
        venueRepository.saveAndFlush(venue);
    }

    private String approvePath() {
        return PATH + "/" + venue.getId() + "/approve";
    }

    @Test
    @DisplayName("Danh sach mac dinh la san cho duyet, moi nhat truoc, kem chu san, so san con va dem theo trang thai duyet")
    void listPending() throws Exception {
        mockMvc.perform(get(PATH).param("q", "san cho duyet moi").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(venue.getId().toString()))
                .andExpect(jsonPath("$.content[0].ownerEmail").value(owner.getEmail()))
                .andExpect(jsonPath("$.content[0].courtCount").value(1))
                .andExpect(jsonPath("$.content[0].approvalStatus").value("pending"))
                .andExpect(jsonPath("$.approvalCounts.pending").isNumber())
                .andExpect(jsonPath("$.approvalCounts.approved").isNumber())
                .andExpect(jsonPath("$.approvalCounts.rejected").isNumber());
        mockMvc.perform(get(PATH).param("approval", "approved").param("q", "san cho duyet moi").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(PATH).param("approval", "all").param("district", "Hải Châu").param("q", "SAN CHO DUYET").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.content[*].id", hasItem(venue.getId().toString())));
        mockMvc.perform(get(PATH).param("district", "Sơn Trà").param("q", "san cho duyet moi").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(PATH).param("size", "1").header("Authorization", bearer(adminToken))).andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("Chi tiet de duyet: thong tin san, chu san, gio hoat dong, gia theo san con va danh sach dieu kien dat")
    void detail() throws Exception {
        mockMvc.perform(get(PATH + "/" + venue.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue.name").value("San Cho Duyet Moi"))
                .andExpect(jsonPath("$.venue.courts", hasSize(1)))
                .andExpect(jsonPath("$.owner.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.hours", hasSize(7)))
                .andExpect(jsonPath("$.courtPrices[0].activeRules").value(1))
                .andExpect(jsonPath("$.courtPrices[0].minPrice").value(120000))
                .andExpect(jsonPath("$.checks", hasSize(4)))
                .andExpect(jsonPath("$.checks[*].ok", not(hasItem(false))))
                .andExpect(jsonPath("$.canApprove").value(true));
        mockMvc.perform(get(PATH + "/" + UUID.randomUUID()).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/abc").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Duyet san: chuyen sang approved + active, ghi nguoi duyet; san hien voi nguoi choi; duyet lai bi chan")
    void approve() throws Exception {
        mockMvc.perform(get("/api/v1/venues/" + venue.getId())).andExpect(status().isNotFound());

        mockMvc.perform(post(approvePath()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue.approvalStatus").value("approved"))
                .andExpect(jsonPath("$.venue.status").value("active"))
                .andExpect(jsonPath("$.approvedBy").value(admin.getFullName()))
                .andExpect(jsonPath("$.approvedAt").exists())
                .andExpect(jsonPath("$.canApprove").value(false));
        mockMvc.perform(get("/api/v1/venues/" + venue.getId())).andExpect(status().isOk());
        mockMvc.perform(post(approvePath()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VENUE_REVIEW_INVALID"));
    }

    @Test
    @DisplayName("San chua du dieu kien (khong co san con hoat dong, gio, gia hoac sai vi tri) khong duoc duyet va noi ro thieu gi")
    void ineligible() throws Exception {
        Court court = courtRepository.findAllWithPriceRules(venue.getId()).getFirst();
        court.getPriceRules().forEach(rule -> rule.setStatus(ActiveStatus.INACTIVE));
        courtRepository.saveAndFlush(court);
        mockMvc.perform(post(approvePath()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.checks").value(org.hamcrest.Matchers.containsString("bang gia")));
        mockMvc.perform(get(PATH + "/" + venue.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.canApprove").value(false));

        court.getPriceRules().forEach(rule -> rule.setStatus(ActiveStatus.ACTIVE));
        court.setStatus(CourtStatus.MAINTENANCE);
        courtRepository.saveAndFlush(court);
        mockMvc.perform(post(approvePath()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.checks").value(org.hamcrest.Matchers.containsString("san con dang hoat dong")));

        court.setStatus(CourtStatus.ACTIVE);
        courtRepository.saveAndFlush(court);
        hoursRepository.findAllByVenueIdOrderByDayOfWeekAsc(venue.getId()).forEach(hours -> hours.setClosed(true));
        hoursRepository.flush();
        mockMvc.perform(post(approvePath()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.checks").value(org.hamcrest.Matchers.containsString("gio hoat dong")));

        hoursRepository.findAllByVenueIdOrderByDayOfWeekAsc(venue.getId()).forEach(hours -> hours.setClosed(false));
        venue.setLatitude(BigDecimal.valueOf(21.03));
        venue.setLongitude(BigDecimal.valueOf(105.85));
        venueRepository.saveAndFlush(venue);
        mockMvc.perform(post(approvePath()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.checks").value(org.hamcrest.Matchers.containsString("Da Nang")));
        mockMvc.perform(get("/api/v1/venues/" + venue.getId())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Chi duyet san dang cho duyet: san bi tu choi hoac da duyet tra 409; san khong ton tai 404")
    void stateRules() throws Exception {
        venue.setApprovalStatus(ApprovalStatus.REJECTED);
        venueRepository.saveAndFlush(venue);
        mockMvc.perform(post(approvePath()).header("Authorization", bearer(adminToken))).andExpect(status().isConflict());
        mockMvc.perform(post(PATH + "/" + UUID.randomUUID() + "/approve").header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/" + venue.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.canApprove").value(false));
    }

    @Test
    @DisplayName("Tham so sai 400; can quyen venue.approve; chu san va nguoi choi 403; chua dang nhap 401")
    void validationAndAccess() throws Exception {
        mockMvc.perform(get(PATH).param("approval", "xyz").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.approval").exists());
        mockMvc.perform(get(PATH).param("district", "Ha Noi").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.district").exists());
        mockMvc.perform(get(PATH).param("size", "101").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(owner)))).andExpect(status().isForbidden());
        mockMvc.perform(post(approvePath()).header("Authorization", bearer(loginAs(persistUser("p", Role.PLAYER))))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "venue.approve".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(post(approvePath()).header("Authorization", bearer(adminToken))).andExpect(status().isForbidden());
    }
}
