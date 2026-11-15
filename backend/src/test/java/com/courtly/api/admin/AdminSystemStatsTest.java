package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.ReportStatus;
import com.courtly.common.enums.ReportTargetType;
import com.courtly.common.enums.VenueStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.review.Report;
import com.courtly.domain.review.ReportRepository;
import com.courtly.domain.venue.Venue;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** 2.3.38 - Xem thong ke he thong. */
class AdminSystemStatsTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/stats/system";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReportRepository reportRepository;

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-stats");
        adminToken = loginAs(admin);
    }

    private JsonNode stats(String... params) throws Exception {
        var request = get(PATH).header("Authorization", bearer(adminToken));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Tong quan day du moi nhom, moi trang thai co khoa (0 neu chua co), tong khop tung phan")
    void overviewIsComplete() throws Exception {
        JsonNode overview = stats().get("overview");
        for (String status : new String[] {"active", "inactive", "banned", "pending"}) {
            assertThat(overview.at("/users/byStatus/" + status).isNumber()).isTrue();
        }
        for (String role : new String[] {"player", "owner", "admin", "staff"}) {
            assertThat(overview.at("/users/byRole/" + role).isNumber()).isTrue();
        }
        for (String approval : new String[] {"pending", "approved", "rejected"}) {
            assertThat(overview.at("/venues/byApproval/" + approval).isNumber()).isTrue();
        }
        for (String status : new String[] {"pending_owner_confirmation", "awaiting_payment", "confirmed", "rejected", "cancelled",
                "completed", "expired", "disputed", "refunded"}) {
            assertThat(overview.at("/bookings/byStatus/" + status).isNumber()).as(status).isTrue();
        }
        for (String status : new String[] {"scheduled", "completed", "disputed", "cancelled"}) {
            assertThat(overview.at("/matches/byStatus/" + status).isNumber()).as(status).isTrue();
        }
        for (String type : new String[] {"user", "venue", "venue_review", "partner_review"}) {
            assertThat(overview.at("/reports/openByType/" + type).isNumber()).as(type).isTrue();
        }
        long byStatus = 0;
        for (JsonNode value : overview.at("/users/byStatus")) {
            byStatus += value.asLong();
        }
        assertThat(overview.at("/users/total").asLong()).isEqualTo(byStatus);
        assertThat(overview.at("/users/newLast30Days").asLong()).isGreaterThanOrEqualTo(overview.at("/users/newLast7Days").asLong());
        assertThat(overview.at("/users/newLast7Days").asLong()).isGreaterThanOrEqualTo(overview.at("/users/newToday").asLong());
        assertThat(overview.at("/venues/total").asLong()).isEqualTo(
                overview.at("/venues/byApproval/pending").asLong() + overview.at("/venues/byApproval/approved").asLong()
                        + overview.at("/venues/byApproval/rejected").asLong());
    }

    @Test
    @DisplayName("So lieu thay doi dung theo du lieu goc: tai khoan, san, booking, bao cao dang cho")
    void overviewReflectsData() throws Exception {
        JsonNode before = stats().get("overview");
        User owner = persistOwner("owner-stats");
        User player = persistUser("player-stats", Role.PLAYER);
        Venue venue = persistVenue(owner, "San Thong Ke");
        createBooking(loginAs(player), venue.getCourts().getFirst(), nextMonday(), LocalTime.of(9, 0));
        venue.setApprovalStatus(ApprovalStatus.PENDING);
        venue.setStatus(VenueStatus.PENDING);
        venueRepository.saveAndFlush(venue);
        newReport(ReportTargetType.USER, ReportStatus.PENDING);
        newReport(ReportTargetType.PARTNER_REVIEW, ReportStatus.REVIEWING);
        newReport(ReportTargetType.USER, ReportStatus.RESOLVED);

        JsonNode after = stats().get("overview");
        assertThat(delta(before, after, "/users/total")).isEqualTo(2);
        assertThat(delta(before, after, "/users/byRole/player")).isEqualTo(1);
        assertThat(delta(before, after, "/users/byRole/owner")).isEqualTo(1);
        assertThat(delta(before, after, "/users/newToday")).isEqualTo(2);
        assertThat(delta(before, after, "/venues/byApproval/pending")).isEqualTo(1);
        assertThat(delta(before, after, "/venues/byApproval/approved")).isZero();
        assertThat(delta(before, after, "/venues/total")).isEqualTo(1);
        assertThat(delta(before, after, "/bookings/total")).isEqualTo(1);
        assertThat(delta(before, after, "/bookings/byStatus/pending_owner_confirmation")).isEqualTo(1);
        assertThat(delta(before, after, "/reports/pending")).isEqualTo(1);
        assertThat(delta(before, after, "/reports/reviewing")).isEqualTo(1);
        assertThat(delta(before, after, "/reports/openByType/user")).isEqualTo(1);
        assertThat(delta(before, after, "/reports/openByType/partner_review")).isEqualTo(1);
    }

    private void newReport(ReportTargetType type, ReportStatus status) {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setReporter(admin);
        report.setTargetType(type);
        report.setTargetId(UUID.randomUUID());
        report.setReason("Thong ke");
        report.setStatus(status);
        report.setCreatedAt(Instant.now());
        reportRepository.saveAndFlush(report);
    }

    private static long delta(JsonNode before, JsonNode after, String path) {
        return after.at(path).asLong() - before.at(path).asLong();
    }

    @Test
    @DisplayName("Xu huong mac dinh: 30 ngay den hom nay (gio Viet Nam), tang dan, diem khong co du lieu bang 0, tong dung")
    void defaultTrend() throws Exception {
        JsonNode trend = stats().get("trend");
        LocalDate today = LocalDate.now(VN);
        assertThat(trend.get("granularity").asText()).isEqualTo("day");
        assertThat(trend.get("to").asText()).isEqualTo(today.toString());
        assertThat(trend.get("from").asText()).isEqualTo(today.minusDays(29).toString());
        assertThat(trend.get("points")).hasSize(30);
        LocalDate previous = null;
        long users = 0;
        for (JsonNode point : trend.get("points")) {
            LocalDate period = LocalDate.parse(point.get("period").asText());
            if (previous != null) {
                assertThat(period).isEqualTo(previous.plusDays(1));
            }
            previous = period;
            users += point.get("newUsers").asLong();
        }
        assertThat(trend.at("/totals/newUsers").asLong()).isEqualTo(users);
    }

    @Test
    @DisplayName("Ngay tinh theo gio Viet Nam: ban ghi luc 00:30 ngay 01/04 (VN) nam o ngay 01/04, khong phai 31/03 theo UTC")
    void vietnamDayBoundary() throws Exception {
        User late = persistUser("bien-ngay-1", Role.PLAYER);
        User early = persistUser("bien-ngay-2", Role.PLAYER);
        setCreated(late, "2020-03-31 16:59:00+00");   // 23:59 ngay 31/03 gio VN
        setCreated(early, "2020-03-31 17:00:00+00");  // 00:00 ngay 01/04 gio VN

        JsonNode trend = stats("from", "2020-03-30", "to", "2020-04-02").get("trend");
        assertThat(trend.get("points")).hasSize(4);
        assertThat(pointUsers(trend, "2020-03-30")).isZero();
        assertThat(pointUsers(trend, "2020-03-31")).isEqualTo(1);
        assertThat(pointUsers(trend, "2020-04-01")).isEqualTo(1);
        assertThat(pointUsers(trend, "2020-04-02")).isZero();
        assertThat(trend.at("/totals/newUsers").asLong()).isEqualTo(2);
    }

    @Test
    @DisplayName("Theo thang: moi thang mot diem tu ngay dau thang, chi dem trong khoang da chon")
    void monthlyTrend() throws Exception {
        setCreated(persistUser("thang-1", Role.PLAYER), "2020-01-15 05:00:00+00");
        setCreated(persistUser("thang-2", Role.PLAYER), "2020-01-31 20:00:00+00");   // 03:00 01/02 gio VN -> thang 2
        setCreated(persistUser("thang-3", Role.PLAYER), "2020-03-10 05:00:00+00");

        JsonNode trend = stats("from", "2020-01-01", "to", "2020-03-31", "granularity", "month").get("trend");
        assertThat(trend.get("granularity").asText()).isEqualTo("month");
        assertThat(trend.get("points")).hasSize(3);
        assertThat(pointUsers(trend, "2020-01-01")).isEqualTo(1);
        assertThat(pointUsers(trend, "2020-02-01")).isEqualTo(1);
        assertThat(pointUsers(trend, "2020-03-01")).isEqualTo(1);

        // Khoang bat dau giua thang: diem dau van la ngay dau thang nhung chi dem tu ngay chon.
        JsonNode partial = stats("from", "2020-01-20", "to", "2020-02-10", "granularity", "month").get("trend");
        assertThat(partial.get("points")).hasSize(2);
        assertThat(pointUsers(partial, "2020-01-01")).isZero();
        assertThat(pointUsers(partial, "2020-02-01")).isEqualTo(1);
    }

    @Test
    @DisplayName("Khoang khong co du lieu (qua khu xa, tuong lai) tra ve 0 chu khong bao loi; mot ngay duy nhat hop le; dung 366 ngay hop le")
    void emptyAndBoundaryRanges() throws Exception {
        JsonNode old = stats("from", "1999-01-01", "to", "1999-01-03").get("trend");
        assertThat(old.get("points")).hasSize(3);
        assertThat(old.at("/totals/newUsers").asLong()).isZero();
        assertThat(old.at("/totals/newBookings").asLong()).isZero();
        LocalDate future = LocalDate.now(VN).plusDays(400);
        JsonNode ahead = stats("from", future.toString(), "to", future.plusDays(1).toString()).get("trend");
        assertThat(ahead.get("points")).hasSize(2);
        assertThat(ahead.at("/totals/newMatches").asLong()).isZero();
        assertThat(stats("from", "2020-05-05", "to", "2020-05-05").at("/trend/points")).hasSize(1);
        assertThat(stats("from", "2019-01-01", "to", "2019-12-31").at("/trend/points")).hasSize(365);
        assertThat(stats("from", "2020-01-01", "to", "2020-12-31").at("/trend/points")).hasSize(366);
    }

    private long pointUsers(JsonNode trend, String period) {
        for (JsonNode point : trend.get("points")) {
            if (period.equals(point.get("period").asText())) {
                return point.get("newUsers").asLong();
            }
        }
        throw new AssertionError("Khong co diem " + period);
    }

    private void setCreated(User user, String timestamp) {
        jdbc.update("UPDATE users SET created_at = ?::timestamptz WHERE id = ?", timestamp, user.getId());
    }

    @Test
    @DisplayName("Tham so sai: ngay ket thuc truoc ngay bat dau, qua 366 ngay, don vi sai, ngay sai dinh dang -> 400")
    void validation() throws Exception {
        mockMvc.perform(get(PATH).param("from", "2020-02-01").param("to", "2020-01-01").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.to").exists());
        mockMvc.perform(get(PATH).param("from", "2020-01-01").param("to", "2021-01-01").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.to").exists());
        mockMvc.perform(get(PATH).param("granularity", "week").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.granularity").exists());
        mockMvc.perform(get(PATH).param("from", "01-01-2020").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("to", "abc").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Chi dat from: den hom nay; chi dat to: 30 ngay den ngay do")
    void partialRange() throws Exception {
        LocalDate today = LocalDate.now(VN);
        JsonNode fromOnly = stats("from", today.minusDays(4).toString()).get("trend");
        assertThat(fromOnly.get("points")).hasSize(5);
        assertThat(fromOnly.get("to").asText()).isEqualTo(today.toString());
        JsonNode toOnly = stats("to", "2020-06-30").get("trend");
        assertThat(toOnly.get("points")).hasSize(30);
        assertThat(toOnly.get("from").asText()).isEqualTo("2020-06-01");
    }

    @Test
    @DisplayName("Can quyen stats.system.view; chu san va nguoi choi 403; chua dang nhap 401")
    void access() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(persistOwner("o"))))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(persistUser("p", Role.PLAYER))))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "stats.system.view".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }
}
