package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.FeeType;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.venue.Venue;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 2.3.42 - Xem tong doanh thu phi nen tang. */
class AdminFeeRevenueTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/finance/fee-revenue";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingCommissionRepository commissionRepository;

    private User admin;
    private String adminToken;
    private User owner;
    private Venue venueA;
    private Venue venueB;
    private String player;
    private int bookingIndex;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-revenue");
        adminToken = loginAs(admin);
        owner = persistOwner("owner-revenue");
        venueA = persistVenue(owner, "San Doanh Thu A");
        venueB = persistVenue(owner, "San Doanh Thu B");
        player = loginAs(persistUser("player-revenue", Role.PLAYER));
    }

    /** Mot hoa hong ghi nhan luc {@code at} gio Viet Nam, gan voi mot booking moi cua {@code venue}. */
    private BookingCommission commission(Venue venue, String status, String gross, String amount, String at) throws Exception {
        String id = createBooking(player, venue.getCourts().getFirst(), nextMonday().plusDays(bookingIndex++), LocalTime.of(9, 0));
        Booking booking = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
        BookingCommission commission = new BookingCommission();
        commission.setId(UUID.randomUUID());
        commission.setBooking(booking);
        commission.setOwner(owner);
        commission.setGrossAmount(new BigDecimal(gross));
        commission.setFeeType(FeeType.PERCENTAGE);
        commission.setFeeValue(BigDecimal.TEN);
        commission.setCommissionAmount(new BigDecimal(amount));
        commission.setStatus(status);
        Instant calculated = ZonedDateTime.of(LocalDate.parse(at.substring(0, 10)), LocalTime.parse(at.substring(11)), VN).toInstant();
        commission.setCalculatedAt(calculated);
        commission.setDueAt(calculated.plusSeconds(7 * 86400));
        commission.setCreatedAt(calculated);
        return commissionRepository.saveAndFlush(commission);
    }

    private JsonNode revenue(String... params) throws Exception {
        var request = get(PATH).header("Authorization", bearer(adminToken));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void sampleData() throws Exception {
        commission(venueA, "settled", "100000", "10000", "2020-03-10T09:00");
        commission(venueA, "due", "200000", "20000", "2020-03-10T20:00");
        commission(venueA, "pending", "300000", "30000", "2020-03-11T09:00");
        commission(venueB, "adjusted", "150000", "15000", "2020-03-11T10:00");
        commission(venueB, "suspended", "400000", "40000", "2020-03-11T11:00");
        commission(venueB, "waived", "500000", "50000", "2020-03-12T11:00");
    }

    @Test
    @DisplayName("Tong hop dung theo trang thai: ghi nhan, da thu, con phai thu; tam hoan va mien hien rieng, khong tinh vao doanh thu")
    void summaryByStatus() throws Exception {
        sampleData();
        JsonNode summary = revenue("from", "2020-03-01", "to", "2020-03-31").get("summary");
        assertThat(summary.get("bookingCount").asLong()).isEqualTo(4);
        assertThat(summary.get("grossAmount").decimalValue()).isEqualByComparingTo("750000");
        assertThat(summary.get("recorded").decimalValue()).isEqualByComparingTo("75000");      // 10+20+30+15 nghin
        assertThat(summary.get("collected").decimalValue()).isEqualByComparingTo("10000");     // settled
        assertThat(summary.get("outstanding").decimalValue()).isEqualByComparingTo("65000");   // due+pending+adjusted
        assertThat(summary.get("effectiveRate").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(summary.get("suspendedCount").asLong()).isEqualTo(1);
        assertThat(summary.get("suspended").decimalValue()).isEqualByComparingTo("40000");
        assertThat(summary.get("waivedCount").asLong()).isEqualTo(1);
        assertThat(summary.get("waived").decimalValue()).isEqualByComparingTo("50000");
        assertThat(summary.get("recorded").decimalValue())
                .isEqualByComparingTo(summary.get("collected").decimalValue().add(summary.get("outstanding").decimalValue()));
    }

    @Test
    @DisplayName("Xu huong theo ngay (gio Viet Nam), diem khong co du lieu bang 0, tong cac diem khop tong hop")
    void dailyTrend() throws Exception {
        sampleData();
        JsonNode body = revenue("from", "2020-03-09", "to", "2020-03-13");
        JsonNode points = body.at("/trend/points");
        assertThat(points).hasSize(5);
        assertThat(body.at("/trend/granularity").asText()).isEqualTo("day");
        assertThat(point(points, "2020-03-09").get("recorded").decimalValue()).isEqualByComparingTo("0");
        assertThat(point(points, "2020-03-10").get("recorded").decimalValue()).isEqualByComparingTo("30000");
        assertThat(point(points, "2020-03-10").get("collected").decimalValue()).isEqualByComparingTo("10000");
        assertThat(point(points, "2020-03-10").get("outstanding").decimalValue()).isEqualByComparingTo("20000");
        assertThat(point(points, "2020-03-10").get("bookingCount").asLong()).isEqualTo(2);
        assertThat(point(points, "2020-03-11").get("recorded").decimalValue()).isEqualByComparingTo("45000"); // pending + adjusted, khong tinh suspended
        assertThat(point(points, "2020-03-12").get("recorded").decimalValue()).isEqualByComparingTo("0");     // chi co waived
        assertThat(point(points, "2020-03-13").get("bookingCount").asLong()).isZero();
        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode item : points) {
            total = total.add(item.get("recorded").decimalValue());
        }
        assertThat(total).isEqualByComparingTo(body.at("/summary/recorded").decimalValue());
    }

    private static JsonNode point(JsonNode points, String period) {
        for (JsonNode item : points) {
            if (period.equals(item.get("period").asText())) {
                return item;
            }
        }
        throw new AssertionError("Khong co diem " + period);
    }

    @Test
    @DisplayName("Ngay tinh theo gio Viet Nam: hoa hong luc 23:59 va 00:00 roi vao hai ngay khac nhau; khoang chon theo gio Viet Nam")
    void vietnamDayBoundary() throws Exception {
        commission(venueA, "due", "100000", "10000", "2020-05-01T23:59");
        commission(venueA, "due", "100000", "20000", "2020-05-02T00:00");
        JsonNode points = revenue("from", "2020-05-01", "to", "2020-05-02").at("/trend/points");
        assertThat(point(points, "2020-05-01").get("recorded").decimalValue()).isEqualByComparingTo("10000");
        assertThat(point(points, "2020-05-02").get("recorded").decimalValue()).isEqualByComparingTo("20000");
        assertThat(revenue("from", "2020-05-02", "to", "2020-05-02").at("/summary/recorded").decimalValue()).isEqualByComparingTo("20000");
        assertThat(revenue("from", "2020-05-01", "to", "2020-05-01").at("/summary/recorded").decimalValue()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("Theo thang: moi thang mot diem, ghi nhan dung thang")
    void monthlyTrend() throws Exception {
        commission(venueA, "due", "100000", "10000", "2020-01-31T20:00");
        commission(venueA, "settled", "100000", "20000", "2020-02-01T01:00");
        commission(venueA, "due", "100000", "30000", "2020-03-15T10:00");
        JsonNode body = revenue("from", "2020-01-01", "to", "2020-03-31", "granularity", "month");
        JsonNode points = body.at("/trend/points");
        assertThat(body.at("/trend/granularity").asText()).isEqualTo("month");
        assertThat(points).hasSize(3);
        assertThat(point(points, "2020-01-01").get("recorded").decimalValue()).isEqualByComparingTo("10000");
        assertThat(point(points, "2020-02-01").get("collected").decimalValue()).isEqualByComparingTo("20000");
        assertThat(point(points, "2020-03-01").get("outstanding").decimalValue()).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("Theo san: moi san mot dong, san nhieu hoa hong len truoc; loc theo san chi con san do; san la 404")
    void byVenue() throws Exception {
        sampleData();
        JsonNode all = revenue("from", "2020-03-01", "to", "2020-03-31");
        assertThat(all.get("venues")).hasSize(2);
        assertThat(all.at("/venues/0/venueName").asText()).isEqualTo("San Doanh Thu A");
        assertThat(all.at("/venues/0/ownerName").asText()).isEqualTo(owner.getFullName());
        assertThat(all.at("/venues/0/bookingCount").asLong()).isEqualTo(3);
        assertThat(all.at("/venues/0/recorded").decimalValue()).isEqualByComparingTo("60000");
        assertThat(all.at("/venues/1/recorded").decimalValue()).isEqualByComparingTo("15000");
        assertThat(all.at("/venues/1/outstanding").decimalValue()).isEqualByComparingTo("15000");
        assertThat(all.get("venueOptions").findValuesAsText("name")).contains("San Doanh Thu A", "San Doanh Thu B");

        JsonNode onlyB = revenue("from", "2020-03-01", "to", "2020-03-31", "venueId", venueB.getId().toString());
        assertThat(onlyB.get("venues")).hasSize(1);
        assertThat(onlyB.at("/summary/recorded").decimalValue()).isEqualByComparingTo("15000");
        assertThat(onlyB.at("/summary/suspended").decimalValue()).isEqualByComparingTo("40000");
        assertThat(onlyB.at("/summary/bookingCount").asLong()).isEqualTo(1);
        mockMvc.perform(get(PATH).param("venueId", UUID.randomUUID().toString()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PATH).param("venueId", "abc").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("San chi co hoa hong tam hoan hoac duoc mien khong hien trong bang theo san; khoang trong tra ve 0 va ty le null")
    void emptyRange() throws Exception {
        commission(venueA, "suspended", "100000", "10000", "2020-07-01T10:00");
        commission(venueA, "waived", "100000", "10000", "2020-07-01T11:00");
        JsonNode body = revenue("from", "2020-07-01", "to", "2020-07-01");
        assertThat(body.get("venues")).isEmpty();
        assertThat(body.at("/summary/recorded").decimalValue()).isEqualByComparingTo("0");
        assertThat(body.at("/summary/bookingCount").asLong()).isZero();
        assertThat(body.at("/summary/effectiveRate").isMissingNode() || body.at("/summary/effectiveRate").isNull()).isTrue();
        assertThat(body.at("/summary/suspendedCount").asLong()).isEqualTo(1);

        JsonNode far = revenue("from", "1999-01-01", "to", "1999-01-03");
        assertThat(far.at("/trend/points")).hasSize(3);
        assertThat(far.at("/summary/grossAmount").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Mac dinh la 30 ngay den hom nay, hoa hong vua ghi nhan duoc tinh")
    void defaultRange() throws Exception {
        JsonNode before = revenue();
        BigDecimal beforeRecorded = before.at("/summary/recorded").decimalValue();
        commission(venueA, "due", "100000", "12345", LocalDate.now(VN).minusDays(2) + "T10:00");
        JsonNode after = revenue();
        assertThat(after.at("/trend/points")).hasSize(30);
        assertThat(after.at("/trend/to").asText()).isEqualTo(LocalDate.now(VN).toString());
        assertThat(after.at("/summary/recorded").decimalValue().subtract(beforeRecorded)).isEqualByComparingTo("12345");
    }

    @Test
    @DisplayName("Tham so sai: ngay ket thuc truoc ngay bat dau, qua 366 ngay, don vi sai, dinh dang ngay sai -> 400")
    void validation() throws Exception {
        mockMvc.perform(get(PATH).param("from", "2020-02-01").param("to", "2020-01-01").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.to").exists());
        mockMvc.perform(get(PATH).param("from", "2020-01-01").param("to", "2021-01-01").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("granularity", "year").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.granularity").exists());
        mockMvc.perform(get(PATH).param("from", "15/03/2020").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        assertThat(revenue("from", "2020-01-01", "to", "2020-12-31").at("/trend/points")).hasSize(366);
    }

    @Test
    @DisplayName("Can quyen finance.commission.view; chu san va nguoi choi 403 du chu san co quyen nay o khu chu san; chua dang nhap 401")
    void access() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(owner)))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(persistUser("p", Role.PLAYER))))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "finance.commission.view".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }
}
