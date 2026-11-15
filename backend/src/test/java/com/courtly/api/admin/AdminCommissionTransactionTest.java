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
import com.courtly.domain.finance.CommissionSettlement;
import com.courtly.domain.finance.CommissionSettlementRepository;
import com.courtly.domain.venue.Venue;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 2.3.43 - Xem danh sach giao dich doi soat. */
class AdminCommissionTransactionTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/finance/commission-transactions";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingCommissionRepository commissionRepository;
    @Autowired private CommissionSettlementRepository settlementRepository;

    private String adminToken;
    private User owner;
    private Venue venueA;
    private Venue venueB;
    private String player;
    private int bookingIndex;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        adminToken = loginAs(persistAdmin("admin-transactions"));
        owner = persistOwner("owner-transactions");
        venueA = persistVenue(owner, "San Giao Dich A");
        venueB = persistVenue(owner, "San Giao Dich B");
        player = loginAs(persistUser("player-transactions", Role.PLAYER));
    }

    private BookingCommission commission(Venue venue, String status, String gross, String amount, String at, Instant dueAt,
                                         CommissionSettlement settlement) throws Exception {
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
        commission.setDueAt(dueAt != null ? dueAt : calculated.plus(7, ChronoUnit.DAYS));
        commission.setCreatedAt(calculated);
        commission.setSettlement(settlement);
        if ("settled".equals(status)) {
            commission.setSettledAt(calculated.plus(2, ChronoUnit.DAYS));
        }
        return commissionRepository.saveAndFlush(commission);
    }

    private CommissionSettlement settlement(String from, String to, String status) {
        CommissionSettlement settlement = new CommissionSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setOwner(owner);
        settlement.setPeriodFrom(LocalDate.parse(from));
        settlement.setPeriodTo(LocalDate.parse(to));
        settlement.setStatus(status);
        return settlementRepository.saveAndFlush(settlement);
    }

    private JsonNode list(String... params) throws Exception {
        var request = get(PATH).header("Authorization", bearer(adminToken));
        java.util.List<String> given = java.util.Arrays.asList(params);
        // Mac dinh gioi han trong nam 2020 de khong lan voi du lieu that; khong ghi de khoang ngay test tu truyen vao.
        if (!given.contains("from")) {
            request = request.param("from", "2020-01-01");
        }
        if (!given.contains("to")) {
            request = request.param("to", "2020-12-31");
        }
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void sampleData() throws Exception {
        CommissionSettlement march = settlement("2020-03-01", "2020-03-31", "pending_review");
        commission(venueA, "settled", "100000", "10000", "2020-03-10T09:00", null, march);
        commission(venueA, "due", "200000", "20000", "2020-03-11T09:00", null, march);
        commission(venueB, "pending", "300000", "30000", "2020-03-12T09:00", null, null);
        commission(venueB, "suspended", "400000", "40000", "2020-03-13T09:00", null, null);
        commission(venueB, "waived", "500000", "50000", "2020-03-14T09:00", null, null);
        commission(venueA, "adjusted", "150000", "15000", "2020-03-15T09:00", null, null);
    }

    @Test
    @DisplayName("Moi dong day du: booking, san, chu san, doanh thu gop, hoa hong, trang thai, han thanh toan, ky doi soat, luc doi soat xong; moi nhat truoc")
    void rowsAreComplete() throws Exception {
        sampleData();
        JsonNode body = list();
        assertThat(body.get("content")).hasSize(6);
        JsonNode newest = body.at("/content/0");
        assertThat(newest.get("status").asText()).isEqualTo("adjusted");
        assertThat(newest.get("venueName").asText()).isEqualTo("San Giao Dich A");
        assertThat(newest.get("ownerName").asText()).isEqualTo(owner.getFullName());
        assertThat(newest.get("bookingCode").asText()).startsWith("CT-");
        assertThat(newest.get("grossAmount").decimalValue()).isEqualByComparingTo("150000");
        assertThat(newest.get("commissionAmount").decimalValue()).isEqualByComparingTo("15000");
        assertThat(newest.has("dueAt")).isTrue();
        assertThat(newest.has("settlement")).isFalse();
        assertThat(newest.has("settledAt")).isFalse();

        JsonNode settled = body.at("/content/5");
        assertThat(settled.get("status").asText()).isEqualTo("settled");
        assertThat(settled.at("/settlement/periodFrom").asText()).isEqualTo("2020-03-01");
        assertThat(settled.at("/settlement/periodTo").asText()).isEqualTo("2020-03-31");
        assertThat(settled.at("/settlement/status").asText()).isEqualTo("pending_review");
        assertThat(settled.has("settledAt")).isTrue();
    }

    @Test
    @DisplayName("Tong cua tap dang loc khong phu thuoc trang dang xem; phan trang dung")
    void totalsAndPaging() throws Exception {
        sampleData();
        JsonNode firstPage = list("size", "2", "page", "0");
        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(6);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(3);
        assertThat(firstPage.at("/totals/count").asLong()).isEqualTo(6);
        assertThat(firstPage.at("/totals/commissionAmount").decimalValue()).isEqualByComparingTo("165000");
        assertThat(firstPage.at("/totals/grossAmount").decimalValue()).isEqualByComparingTo("1650000");
        JsonNode lastPage = list("size", "2", "page", "2");
        assertThat(lastPage.get("content")).hasSize(2);
        assertThat(lastPage.at("/totals/commissionAmount").decimalValue()).isEqualByComparingTo("165000");
        assertThat(list("size", "2", "page", "9").get("content")).isEmpty();
    }

    @Test
    @DisplayName("Loc theo trang thai, san, ky doi soat: chi tap khop; tong tinh dung tap khop")
    void filters() throws Exception {
        sampleData();
        JsonNode due = list("status", "due");
        assertThat(due.get("content")).hasSize(1);
        assertThat(due.at("/totals/commissionAmount").decimalValue()).isEqualByComparingTo("20000");
        assertThat(list("status", "suspended").at("/totals/commissionAmount").decimalValue()).isEqualByComparingTo("40000");
        assertThat(list("status", "waived").get("content")).hasSize(1);

        JsonNode byVenue = list("venueId", venueB.getId().toString());
        assertThat(byVenue.get("content")).hasSize(3);
        assertThat(byVenue.at("/totals/commissionAmount").decimalValue()).isEqualByComparingTo("120000");
        assertThat(list("venueId", venueB.getId().toString(), "status", "pending").get("content")).hasSize(1);

        String settlementId = list().at("/content/5/settlement/id").asText();
        JsonNode bySettlement = list("settlementId", settlementId);
        assertThat(bySettlement.get("content")).hasSize(2);
        assertThat(bySettlement.at("/totals/commissionAmount").decimalValue()).isEqualByComparingTo("30000");
        assertThat(bySettlement.get("settlements").findValuesAsText("id")).contains(settlementId);
        assertThat(bySettlement.get("venues").findValuesAsText("name")).contains("San Giao Dich A", "San Giao Dich B");
    }

    @Test
    @DisplayName("Tim theo ma booking khong phan biet hoa thuong, co the tim mot phan; khong khop thi rong")
    void searchByBookingCode() throws Exception {
        sampleData();
        String code = list().at("/content/2/bookingCode").asText();
        JsonNode exact = list("q", code.toLowerCase());
        assertThat(exact.get("content")).hasSize(1);
        assertThat(exact.at("/content/0/bookingCode").asText()).isEqualTo(code);
        assertThat(list("q", code.substring(3, code.length() - 1).toUpperCase()).get("content").size()).isGreaterThanOrEqualTo(1);
        assertThat(list("q", "KHONG-CO-MA-NAY").get("content")).isEmpty();
        assertThat(list("q", "%").get("content")).isEmpty();
        assertThat(list("q", "_").get("content")).isEmpty();
        assertThat(list("q", "KHONG-CO-MA-NAY").at("/totals/count").asLong()).isZero();
    }

    @Test
    @DisplayName("Khoan qua han duoc danh dau: chi chua thu (pending, due, adjusted) va da qua han; loc rieng khoan qua han")
    void overdueHighlight() throws Exception {
        Instant past = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant future = Instant.now().plus(3, ChronoUnit.DAYS);
        commission(venueA, "due", "100000", "10000", "2020-04-01T09:00", past, null);
        commission(venueA, "pending", "100000", "20000", "2020-04-02T09:00", past, null);
        commission(venueA, "adjusted", "100000", "30000", "2020-04-03T09:00", past, null);
        commission(venueA, "due", "100000", "40000", "2020-04-04T09:00", future, null);
        commission(venueA, "settled", "100000", "50000", "2020-04-05T09:00", past, null);
        commission(venueA, "suspended", "100000", "60000", "2020-04-06T09:00", past, null);
        commission(venueA, "waived", "100000", "70000", "2020-04-07T09:00", past, null);

        JsonNode all = list();
        int overdue = 0;
        for (JsonNode row : all.get("content")) {
            if (row.get("overdue").asBoolean()) {
                overdue++;
                assertThat(row.get("status").asText()).isIn("pending", "due", "adjusted");
            }
        }
        assertThat(overdue).isEqualTo(3);
        JsonNode only = list("overdue", "true");
        assertThat(only.get("content")).hasSize(3);
        assertThat(only.at("/totals/commissionAmount").decimalValue()).isEqualByComparingTo("60000");
        assertThat(list("overdue", "true", "status", "settled").get("content")).isEmpty();
        assertThat(list("overdue", "false").get("content")).hasSize(7);
    }

    @Test
    @DisplayName("Khoang thoi gian ghi nhan tinh theo gio Viet Nam, tinh ca ngay dau va ngay cuoi")
    void dateRange() throws Exception {
        commission(venueA, "due", "100000", "10000", "2020-06-01T00:00", null, null);
        commission(venueA, "due", "100000", "20000", "2020-06-01T23:59", null, null);
        commission(venueA, "due", "100000", "30000", "2020-06-02T00:00", null, null);
        commission(venueA, "due", "100000", "40000", "2020-05-31T23:59", null, null);
        JsonNode firstDay = list("from", "2020-06-01", "to", "2020-06-01");
        assertThat(firstDay.get("content")).hasSize(2);
        assertThat(firstDay.at("/totals/commissionAmount").decimalValue()).isEqualByComparingTo("30000");
        assertThat(list("from", "2020-06-02", "to", "2020-06-02").get("content")).hasSize(1);
        assertThat(list("from", "2020-05-31", "to", "2020-06-02").get("content")).hasSize(4);
        assertThat(list("from", "2020-06-03", "to", "2020-06-10").get("content")).isEmpty();
    }

    @Test
    @DisplayName("Tham so sai 400; san hoac ky doi soat khong ton tai 404; chi doc")
    void validation() throws Exception {
        mockMvc.perform(get(PATH).param("status", "xyz").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
        mockMvc.perform(get(PATH).param("from", "2020-02-01").param("to", "2020-01-01").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.to").exists());
        mockMvc.perform(get(PATH).param("size", "101").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("size", "0").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("page", "-1").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("q", "x".repeat(101)).header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("from", "01/01/2020").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("venueId", UUID.randomUUID().toString()).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH).param("settlementId", UUID.randomUUID().toString()).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH).param("venueId", "abc").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("from", "1999-01-01").param("to", "1999-01-02").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0))).andExpect(jsonPath("$.totals.count").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @DisplayName("Can quyen finance.commission.view; chu san va nguoi choi 403; chua dang nhap 401")
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
