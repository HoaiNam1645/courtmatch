package com.courtly.api.admin;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.SkillLevel;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 2.3.31 - Quan ly tai khoan nguoi choi. */
class AdminPlayerManagementTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/players";

    @Autowired private BookingRepository bookingRepository;

    private User admin;
    private String adminToken;
    private User player;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-players");
        adminToken = loginAs(admin);
        player = persistUser("player-managed", Role.PLAYER);
        player.setFullName("Trần Thị Người Chơi");
        player.getPlayerProfile().setSkillLevel(SkillLevel.ADVANCED);
        userRepository.saveAndFlush(player);
    }

    private ResultActions lock(String token, UUID id, String body) throws Exception {
        return mockMvc.perform(post(PATH + "/" + id + "/lock").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("Danh sach chi gom nguoi choi; tim khong dau; loc trinh do, trang thai, ngay dang ky; kem so booking")
    void listAndFilter() throws Exception {
        User owner = persistOwner("owner-not-player");
        Venue venue = persistVenue(owner, "San Cua Chu");
        Court court = venue.getCourts().getFirst();
        createBooking(loginAs(player), court, nextMonday(), LocalTime.of(9, 0));
        createBooking(loginAs(player), court, nextMonday(), LocalTime.of(12, 0));

        mockMvc.perform(get(PATH).param("q", "tran thi nguoi choi").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(player.getId().toString()))
                .andExpect(jsonPath("$.content[0].skillLevel").value("advanced"))
                .andExpect(jsonPath("$.content[0].bookingCount").value(2))
                .andExpect(jsonPath("$.statusCounts.active").isNumber());
        mockMvc.perform(get(PATH).param("q", owner.getEmail()).header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(PATH).param("q", "tran thi nguoi choi").param("skill", "beginner").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(PATH).param("q", "tran thi nguoi choi").param("skill", "advanced").param("status", "active")
                        .param("from", java.time.LocalDate.now().toString()).param("to", java.time.LocalDate.now().toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(PATH).param("q", "tran thi nguoi choi").param("from", java.time.LocalDate.now().plusDays(1).toString())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(PATH).param("size", "1").header("Authorization", bearer(adminToken)))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("Chi tiet: ho so, thong ke booking theo trang thai, booking gan day, so tran va so lan bi bao cao")
    void detail() throws Exception {
        Venue venue = persistVenue(persistOwner("owner-detail"), "San Chi Tiet");
        createBooking(loginAs(player), venue.getCourts().getFirst(), nextMonday(), LocalTime.of(9, 0));

        mockMvc.perform(get(PATH + "/" + player.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.email").value(player.getEmail()))
                .andExpect(jsonPath("$.account.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.skillLevel").value("advanced"))
                .andExpect(jsonPath("$.bookingCount").value(1))
                .andExpect(jsonPath("$.bookingsByStatus.pending_owner_confirmation").value(1))
                .andExpect(jsonPath("$.recentBookings", hasSize(1)))
                .andExpect(jsonPath("$.recentBookings[0].venueName").value("San Chi Tiet"))
                .andExpect(jsonPath("$.matchCount").value(0))
                .andExpect(jsonPath("$.reportCount").value(0));
        mockMvc.perform(get(PATH + "/" + UUID.randomUUID()).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
        // Chu san khong phai nguoi choi: khong xem duoc o day.
        mockMvc.perform(get(PATH + "/" + persistOwner("o2").getId()).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Khoa nguoi choi kem ly do: khong dang nhap, khong dat san moi, booking da co van giu; mo khoa dung lai duoc")
    void lockAndUnlock() throws Exception {
        Venue venue = persistVenue(persistOwner("owner-lock"), "San Khoa");
        String playerToken = loginAs(player);
        String bookingId = createBooking(playerToken, venue.getCourts().getFirst(), nextMonday(), LocalTime.of(9, 0));

        lock(adminToken, player.getId(), "{\"reason\":\"Huy san lien tuc\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.account.status").value("banned"))
                .andExpect(jsonPath("$.account.lastStatusChange.reason").value("Huy san lien tuc"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailOrPhone\":\"%s\",\"password\":\"%s\"}".formatted(player.getEmail(), PASSWORD)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/bookings").header("Authorization", bearer(playerToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courtId":"%s","startTime":"%s","durationMinutes":60}
                                """.formatted(venue.getCourts().getFirst().getId(),
                                nextMonday().atTime(15, 0).atZone(ZONE).toInstant())))
                .andExpect(status().isForbidden());
        org.junit.jupiter.api.Assertions.assertEquals("pending_owner_confirmation",
                bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow().getStatus().getValue());

        mockMvc.perform(post(PATH + "/" + player.getId() + "/unlock").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.account.status").value("active"));
        loginAs(player);
    }

    @Test
    @DisplayName("Khoa bat buoc ly do; khoa lai / mo khoa nguoi dang hoat dong bi chan; khong khoa chu san hay chinh minh qua man hinh nay")
    void lockRules() throws Exception {
        lock(adminToken, player.getId(), "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        lock(adminToken, player.getId(), "{\"reason\":\"ab\"}").andExpect(status().isBadRequest());
        mockMvc.perform(post(PATH + "/" + player.getId() + "/unlock").header("Authorization", bearer(adminToken))).andExpect(status().isConflict());
        lock(adminToken, player.getId(), "{\"reason\":\"Vi pham noi quy\"}").andExpect(status().isOk());
        lock(adminToken, player.getId(), "{\"reason\":\"Vi pham noi quy\"}").andExpect(status().isConflict());
        lock(adminToken, persistOwner("o3").getId(), "{\"reason\":\"Khong phai nguoi choi\"}").andExpect(status().isNotFound());
        lock(adminToken, UUID.randomUUID(), "{\"reason\":\"Khong ton tai\"}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tham so sai 400; can quyen user.manage; nguoi choi 403; chua dang nhap 401")
    void validationAndAccess() throws Exception {
        mockMvc.perform(get(PATH).param("status", "xyz").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
        mockMvc.perform(get(PATH).param("skill", "god").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.skill").exists());
        mockMvc.perform(get(PATH).param("from", "2026-11-10").param("to", "2026-11-01").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("size", "101").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH + "/abc").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());

        String playerToken = loginAs(player);
        mockMvc.perform(get(PATH).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        lock(playerToken, player.getId(), "{\"reason\":\"Tu khoa\"}").andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "user.manage".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        lock(adminToken, player.getId(), "{\"reason\":\"Khong du quyen\"}").andExpect(status().isForbidden());
    }
}
