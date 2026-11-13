package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtPriceRule;
import com.courtly.domain.venue.Venue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** 2.3.5 - Cap nhat gia san. */
class OwnerPriceRuleTest extends OwnerApiTestBase {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private String ownerToken;
    private Venue venue;
    private Court court;
    private CourtPriceRule baseRule;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        User owner = persistOwner("owner-price");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Bang Gia");
        court = venue.getCourts().getFirst();
        baseRule = court.getPriceRules().getFirst();
    }

    @Test
    @DisplayName("Xem bang gia theo san con")
    void listRules() throws Exception {
        mockMvc.perform(get(venuePath() + "/price-rules").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courts", hasSize(1)))
                .andExpect(jsonPath("$.courts[0].courtCode").value("A"))
                .andExpect(jsonPath("$.courts[0].rules", hasSize(1)))
                .andExpect(jsonPath("$.courts[0].rules[0].startTime").value("06:00"))
                .andExpect(jsonPath("$.courts[0].rules[0].endTime").value("22:00"))
                .andExpect(jsonPath("$.courts[0].rules[0].pricePerHour").value(120000))
                .andExpect(jsonPath("$.courts[0].rules[0].status").value("active"));
    }

    @Test
    @DisplayName("Chong lan bi 409; tach khung gio roi them gio cao diem; bao gia dung gia moi; ghi audit")
    void splitIntoPeakHours() throws Exception {
        mockMvc.perform(post(rulesPath())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody(null, "17:00", "22:00", 150000, null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_RULE_OVERLAP"))
                .andExpect(jsonPath("$.fieldErrors.startTime").exists());

        mockMvc.perform(put(rulesPath() + "/" + baseRule.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody(null, "06:00", "17:00", 120000, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endTime").value("17:00"))
                // Giu nguyen ngay bat dau cu (da qua) khi khong gui effectiveFrom.
                .andExpect(jsonPath("$.effectiveFrom").value(baseRule.getEffectiveFrom().toString()));

        mockMvc.perform(post(rulesPath())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody(null, "17:00", "22:00", 150000, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pricePerHour").value(150000))
                .andExpect(jsonPath("$.effectiveFrom").value(LocalDate.now(ZONE).toString()));

        // 16:30-18:00: 30 phut gia thuong + 60 phut gia cao diem = 60.000 + 150.000.
        mockMvc.perform(post("/api/v1/bookings/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courtId":"%s","startTime":"%s","durationMinutes":90}
                                """.formatted(court.getId(), nextMonday().atTime(16, 30).atZone(ZONE).toInstant())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(210000));

        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("price_rule.update");
            assertThat(log.getOldData()).containsEntry("endTime", "22:00");
            assertThat(log.getNewData()).containsEntry("endTime", "17:00");
        });
    }

    @Test
    @DisplayName("Doi gia khong lam doi so tien cua booking da tao")
    void existingBookingKeepsPrice() throws Exception {
        User player = persistUser("player", Role.PLAYER);
        String bookingId = createBooking(loginAs(player), court, nextMonday(), LocalTime.of(9, 0));

        mockMvc.perform(put(rulesPath() + "/" + baseRule.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody(null, "06:00", "22:00", 200000, null, null)))
                .andExpect(status().isOk());

        assertThat(bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow().getTotalAmount())
                .isEqualByComparingTo(new BigDecimal("180000"));
        mockMvc.perform(post("/api/v1/bookings/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courtId":"%s","startTime":"%s","durationMinutes":90}
                                """.formatted(court.getId(), nextMonday().atTime(14, 0).atZone(ZONE).toInstant())))
                .andExpect(jsonPath("$.totalAmount").value(300000));
    }

    @Test
    @DisplayName("Ngung ap dung roi tao gia rieng tung thu; khong sua / ngung lai quy tac da ngung")
    void deactivateAndDaySpecificRules() throws Exception {
        mockMvc.perform(delete(rulesPath() + "/" + baseRule.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(rulesPath() + "/" + baseRule.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_RULE_STATE_INVALID"));
        mockMvc.perform(put(rulesPath() + "/" + baseRule.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody(null, "06:00", "22:00", 100000, null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRICE_RULE_STATE_INVALID"));

        for (int day : new int[] {1, 6}) {
            mockMvc.perform(post(rulesPath())
                            .header("Authorization", bearer(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ruleBody(day, "06:00", "22:00", day == 6 ? 160000 : 110000, null, null)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.dayOfWeek").value(day));
        }
        // Quy tac moi ngay se trung voi ca Thu hai lan Thu bay.
        mockMvc.perform(post(rulesPath())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody(null, "21:00", "22:00", 90000, null, null)))
                .andExpect(status().isConflict());

        mockMvc.perform(get(venuePath() + "/price-rules").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.courts[0].rules", hasSize(3)))
                .andExpect(jsonPath("$.courts[0].rules[?(@.status=='inactive')]", hasSize(1)));
    }

    @Test
    @DisplayName("Quy tac gia co thoi han: khong chong lan voi giai doan sau")
    void effectivePeriods() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        mockMvc.perform(put(rulesPath() + "/" + baseRule.getId())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody(null, "06:00", "22:00", 120000, null, today.plusDays(9).toString())))
                .andExpect(status().isOk());
        mockMvc.perform(post(rulesPath())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody(null, "06:00", "22:00", 130000, today.plusDays(10).toString(), null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(rulesPath())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody(null, "06:00", "08:00", 99000, today.plusDays(5).toString(), today.plusDays(11).toString())))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Kiem tra du lieu: gio, ngay, gia, thu trong tuan, dinh dang gio")
    void validation() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        expectBadRequest(ruleBody(null, "18:00", "17:00", 100000, null, null), "endTime");
        expectBadRequest(ruleBody(null, "22:00", "23:00", 100000, today.minusDays(1).toString(), null), "effectiveFrom");
        expectBadRequest(ruleBody(null, "22:00", "23:00", 100000, today.plusDays(5).toString(), today.plusDays(1).toString()), "effectiveTo");
        expectBadRequest(ruleBody(null, "22:00", "23:00", -1, null, null), "pricePerHour");
        expectBadRequest(ruleBody(null, "22:00", "23:00", 0.5, null, null), "pricePerHour");
        expectBadRequest(ruleBody(8, "22:00", "23:00", 100000, null, null), "dayOfWeek");
        expectBadRequest("""
                {"startTime":"25:00","endTime":"23:00","pricePerHour":100000}
                """, "startTime");
        expectBadRequest("{}", "pricePerHour");
    }

    @Test
    @DisplayName("San con / quy tac cua chu san khac tra 404")
    void ownership() throws Exception {
        Venue other = persistVenue(persistOwner("other"), "San Nguoi Khac");
        Court otherCourt = other.getCourts().getFirst();
        mockMvc.perform(get("/api/v1/owner/venues/" + other.getId() + "/price-rules").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/owner/venues/" + other.getId() + "/courts/" + otherCourt.getId() + "/price-rules")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ruleBody(null, "22:00", "23:00", 100000, null, null)))
                .andExpect(status().isNotFound());
        // Quy tac cua san khac nhung gan vao duong dan san cua minh.
        mockMvc.perform(delete(rulesPath() + "/" + otherCourt.getPriceRules().getFirst().getId())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Rang buoc database chan quy tac chong lan du bo qua tang nghiep vu")
    void databaseConstraint() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO court_price_rules (court_id, day_of_week, start_time, end_time, price_per_hour)
                VALUES (?, 3, '10:00', '11:00', 1)
                """, court.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void expectBadRequest(String body, String field) throws Exception {
        mockMvc.perform(post(rulesPath())
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors." + field).exists());
    }

    private String venuePath() {
        return "/api/v1/owner/venues/" + venue.getId();
    }

    private String rulesPath() {
        return venuePath() + "/courts/" + court.getId() + "/price-rules";
    }

    private static String ruleBody(Integer day, String start, String end, Number price, String from, String to) {
        return """
                {"dayOfWeek":%s,"startTime":"%s","endTime":"%s","pricePerHour":%s,"effectiveFrom":%s,"effectiveTo":%s}
                """.formatted(day, start, end, price, from == null ? "null" : "\"" + from + "\"",
                to == null ? "null" : "\"" + to + "\"");
    }
}
