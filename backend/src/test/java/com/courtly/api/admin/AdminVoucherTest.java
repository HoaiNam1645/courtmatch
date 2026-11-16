package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.system.AuditLog;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.domain.voucher.Voucher;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.voucher.VoucherRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** B4 - quan tri vien quan ly voucher. */
class AdminVoucherTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/vouchers";

    @Autowired private VoucherRepository voucherRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private User admin;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        admin = persistAdmin("admin-voucher");
        adminToken = loginAs(admin);
    }

    private static String body(String code, String percent, String cap, int fromDays, int untilDays, String limit, String elo, String matches, String weeks) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return """
                {"code":"%s","name":"Voucher thu nghiem","description":"Mo ta","discountPercent":%s,"maxDiscountAmount":%s,
                 "validFrom":"%s","validUntil":"%s","totalLimit":%s,"minElo":%s,"minBookingMatches":%s,"minActiveWeeks":%s}
                """.formatted(code, percent, cap, now.plus(fromDays, ChronoUnit.DAYS), now.plus(untilDays, ChronoUnit.DAYS), limit, elo, matches, weeks);
    }

    private static String valid(String code) {
        return body(code, "15", "50000", -1, 30, "100", "1100", "2", "1");
    }

    private org.springframework.test.web.servlet.ResultActions create(String json) throws Exception {
        return mockMvc.perform(post(PATH).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private org.springframework.test.web.servlet.ResultActions update(UUID id, String json) throws Exception {
        return mockMvc.perform(put(PATH + "/" + id).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private UUID createdId(String code) throws Exception {
        return UUID.fromString(objectMapper.readTree(create(valid(code)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .at("/summary/id").asText());
    }

    private AuditLog audit(String action) {
        return auditLogRepository.findAll().stream().filter(item -> action.equals(item.getAction()) && item.getActor() != null
                && admin.getId().equals(item.getActor().getId())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Tao voucher: luu day du, ma duoc viet hoa va gan nguoi tao, ghi nhat ky; chua dung nen chi phi bang 0")
    void createsVoucher() throws Exception {
        create(valid("sale-1111"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.code").value("SALE-1111"))
                .andExpect(jsonPath("$.summary.name").value("Voucher thu nghiem"))
                .andExpect(jsonPath("$.summary.discountPercent").value(15.0))
                .andExpect(jsonPath("$.summary.maxDiscountAmount").value(50000))
                .andExpect(jsonPath("$.summary.totalLimit").value(100))
                .andExpect(jsonPath("$.summary.minElo").value(1100.0))
                .andExpect(jsonPath("$.summary.minBookingMatches").value(2))
                .andExpect(jsonPath("$.summary.minActiveWeeks").value(1))
                .andExpect(jsonPath("$.summary.status").value("active"))
                .andExpect(jsonPath("$.summary.state").value("active"))
                .andExpect(jsonPath("$.summary.usedCount").value(0))
                .andExpect(jsonPath("$.summary.remaining").value(100))
                .andExpect(jsonPath("$.summary.totalDiscount").value(0))
                .andExpect(jsonPath("$.description").value("Mo ta"))
                .andExpect(jsonPath("$.createdBy").value(admin.getFullName()))
                .andExpect(jsonPath("$.redemptions", hasSize(0)));
        Voucher saved = voucherRepository.findByCode("sale-1111").orElseThrow();
        assertThat(saved.getCreatedBy().getId()).isEqualTo(admin.getId());
        assertThat(audit("voucher.create").getNewData()).containsEntry("code", "SALE-1111");
    }

    @Test
    @DisplayName("De trong gioi han va dieu kien: khong yeu cau gi; phan tram thap phan va tran giam duoc luu dung")
    void optionalFields() throws Exception {
        create(body("FREE-FOR-ALL", "7.5", "null", -1, 10, "null", "null", "null", "null")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.discountPercent").value(7.5))
                .andExpect(jsonPath("$.summary.maxDiscountAmount").doesNotExist())
                .andExpect(jsonPath("$.summary.totalLimit").doesNotExist())
                .andExpect(jsonPath("$.summary.remaining").doesNotExist())
                .andExpect(jsonPath("$.summary.minElo").doesNotExist());
        create(body("FULL-100", "100", "null", -1, 10, "1", "0", "1", "1")).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Ma trung (khong phan biet hoa thuong) 409; du lieu sai 400: ma, ten, phan tram, tran giam, ngay, gioi han, dieu kien")
    void createValidation() throws Exception {
        createdId("DUP-CODE");
        create(valid("dup-code")).andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.code").exists());
        create(valid("ab")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.code").exists());
        create(valid("co dau cach")).andExpect(status().isBadRequest());
        create(valid("x".repeat(41))).andExpect(status().isBadRequest());
        create(body("VAL-0", "0", "null", -1, 5, "null", "null", "null", "null")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.discountPercent").exists());
        create(body("VAL-101", "100.01", "null", -1, 5, "null", "null", "null", "null")).andExpect(status().isBadRequest());
        create(body("VAL-3DEC", "10.123", "null", -1, 5, "null", "null", "null", "null")).andExpect(status().isBadRequest());
        create(body("VAL-CAP", "10", "0", -1, 5, "null", "null", "null", "null")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.maxDiscountAmount").exists());
        create(body("VAL-CAP2", "10", "100000001", -1, 5, "null", "null", "null", "null")).andExpect(status().isBadRequest());
        create(body("VAL-DATE", "10", "null", 5, 5, "null", "null", "null", "null")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.validUntil").exists());
        create(body("VAL-DATE2", "10", "null", 5, 1, "null", "null", "null", "null")).andExpect(status().isBadRequest());
        create(body("VAL-LIM", "10", "null", -1, 5, "0", "null", "null", "null")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.totalLimit").exists());
        create(body("VAL-ELO", "10", "null", -1, 5, "null", "-1", "null", "null")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.minElo").exists());
        create(body("VAL-ELO2", "10", "null", -1, 5, "null", "5001", "null", "null")).andExpect(status().isBadRequest());
        create(body("VAL-MAT", "10", "null", -1, 5, "null", "null", "0", "null")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.minBookingMatches").exists());
        create(body("VAL-WK", "10", "null", -1, 5, "null", "null", "null", "521")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.minActiveWeeks").exists());
        create("{\"code\":\"VAL-NONAME\",\"discountPercent\":10,\"validFrom\":\"2030-01-01T00:00:00Z\",\"validUntil\":\"2030-02-01T00:00:00Z\"}")
                .andExpect(status().isBadRequest());
        create("khong phai json").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Danh sach: trang thai hien tai (active, locked, scheduled, expired, exhausted), loc theo trang thai va tu khoa, tong chi phi Courtly chiu")
    void listFiltersAndTotals() throws Exception {
        UUID active = createdId("LST-ACTIVE");
        UUID locked = createdId("LST-LOCKED");
        mockMvc.perform(post(PATH + "/" + locked + "/lock").header("Authorization", bearer(adminToken))).andExpect(status().isOk());
        create(body("LST-SOON", "10", "null", 5, 20, "null", "null", "null", "null")).andExpect(status().isCreated());
        create(body("LST-OLD", "10", "null", -10, -1, "null", "null", "null", "null")).andExpect(status().isCreated());

        JsonNode all = list("q", "LST-");
        assertThat(all.get("content")).hasSize(4);
        assertThat(states(all)).containsExactlyInAnyOrder("active", "locked", "scheduled", "expired");
        assertThat(list("q", "lst-", "state", "active").get("content")).hasSize(1);
        assertThat(list("q", "LST-", "state", "locked").at("/content/0/code").asText()).isEqualTo("LST-LOCKED");
        assertThat(list("q", "LST-", "state", "scheduled").get("content")).hasSize(1);
        assertThat(list("q", "LST-", "state", "expired").get("content")).hasSize(1);
        assertThat(list("q", "thu nghiem").get("content").size()).isGreaterThanOrEqualTo(4);
        assertThat(list("q", "KHONG-CO-XYZ").get("content")).isEmpty();
        assertThat(list("q", "KHONG-CO-XYZ").at("/totals/voucherCount").asLong()).isZero();
        assertThat(active).isNotNull();
        mockMvc.perform(get(PATH).param("state", "xyz").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.state").exists());
        mockMvc.perform(get(PATH).param("q", "x".repeat(101)).header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
    }

    private JsonNode list(String... params) throws Exception {
        var request = get(PATH).header("Authorization", bearer(adminToken));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static java.util.List<String> states(JsonNode list) {
        java.util.List<String> result = new java.util.ArrayList<>();
        list.get("content").forEach(item -> result.add(item.get("state").asText()));
        return result;
    }

    @Test
    @DisplayName("Sua voucher: doi ten, han, gioi han, dieu kien; ma khong doi; chua dung thi doi duoc muc giam; khong dat gioi han duoi so luot da dung")
    void updatesVoucher() throws Exception {
        UUID id = createdId("UPD-1");
        update(id, body("UPD-1", "25", "80000", -1, 60, "200", "1300", "5", "3")).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.discountPercent").value(25.0))
                .andExpect(jsonPath("$.summary.maxDiscountAmount").value(80000))
                .andExpect(jsonPath("$.summary.totalLimit").value(200))
                .andExpect(jsonPath("$.summary.minElo").value(1300.0))
                .andExpect(jsonPath("$.summary.minActiveWeeks").value(3));
        AuditLog log = audit("voucher.update");
        assertThat(log.getOldData()).containsEntry("discountPercent", new java.math.BigDecimal("15.00"));
        assertThat(log.getNewData()).containsEntry("discountPercent", new java.math.BigDecimal("25.00"));
        update(id, valid("DOI-MA-KHAC")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.code").exists());
        update(id, valid("upd-1")).andExpect(status().isOk());
        update(UUID.randomUUID(), valid("UPD-1")).andExpect(status().isNotFound());
        mockMvc.perform(put(PATH + "/abc").header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(valid("UPD-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Da co luot dung thi khong doi duoc muc giam va tran giam; luot dung bang co tong tien Courtly chiu, luot da tra lai khong tinh")
    void usageFreezesDiscountAndCountsCost() throws Exception {
        UUID id = createdId("USE-1");
        User owner = persistOwner("owner-usage");
        Venue venue = persistVenue(owner, "San Usage");
        String playerA = loginAs(persistUser("player-a", Role.PLAYER));
        String playerB = loginAs(persistUser("player-b", Role.PLAYER));
        // Dieu kien trong voucher mac dinh yeu cau Elo 1100: bo dieu kien de dung duoc.
        update(id, body("USE-1", "10", "null", -1, 30, "5", "null", "null", "null")).andExpect(status().isOk());
        String first = bookWith(playerA, venue, "USE-1", 0);
        String second = bookWith(playerB, venue, "USE-1", 1);

        JsonNode detail = objectMapper.readTree(mockMvc.perform(get(PATH + "/" + id).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(detail.at("/summary/usedCount").asLong()).isEqualTo(2);
        assertThat(detail.at("/summary/totalDiscount").decimalValue()).isEqualByComparingTo("36000");
        assertThat(detail.at("/summary/remaining").asInt()).isEqualTo(3);
        assertThat(detail.get("redemptions")).hasSize(2);
        assertThat(detail.at("/redemptions/0/status").asText()).isEqualTo("applied");
        assertThat(detail.at("/redemptions/0/discountAmount").decimalValue()).isEqualByComparingTo("18000");
        assertThat(detail.at("/redemptions/0/bookingCode").asText()).startsWith("CT-");

        mockMvc.perform(post("/api/v1/bookings/" + first + "/cancel").header("Authorization", bearer(playerA))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Doi lich\"}")).andExpect(status().isOk());
        JsonNode after = objectMapper.readTree(mockMvc.perform(get(PATH + "/" + id).header("Authorization", bearer(adminToken)))
                .andReturn().getResponse().getContentAsString());
        assertThat(after.at("/summary/usedCount").asLong()).isEqualTo(1);
        assertThat(after.at("/summary/totalDiscount").decimalValue()).isEqualByComparingTo("18000");
        assertThat(after.get("redemptions")).hasSize(2);
        assertThat(after.findValuesAsText("status")).contains("released", "applied");
        assertThat(second).isNotNull();
        JsonNode totals = list("q", "USE-1").get("totals");
        assertThat(totals.get("redemptions").asLong()).isEqualTo(1);
        assertThat(totals.get("totalDiscount").decimalValue()).isEqualByComparingTo("18000");

        update(id, body("USE-1", "20", "null", -1, 30, "5", "null", "null", "null")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.discountPercent").exists());
        update(id, body("USE-1", "10", "20000", -1, 30, "5", "null", "null", "null")).andExpect(status().isBadRequest());
        update(id, body("USE-1", "10", "null", -1, 30, "0", "null", "null", "null")).andExpect(status().isBadRequest());
        update(id, body("USE-1", "10", "null", -1, 30, "1", "null", "null", "null")).andExpect(status().isOk());   // = so luot dang dung
        update(id, body("USE-1", "10", "null", -1, 90, "50", "1200", "null", "null")).andExpect(status().isOk());
    }

    private String bookWith(String token, Venue venue, String code, int offset) throws Exception {
        String start = nextMonday().plusDays(offset).atTime(LocalTime.of(9, 0)).atZone(ZONE).toInstant().toString();
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/bookings").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"courtId\":\"%s\",\"startTime\":\"%s\",\"durationMinutes\":90,\"voucherCode\":\"%s\"}".formatted(venue.getCourts().getFirst().getId(), start, code)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    @DisplayName("Khoa va mo khoa: khoa thi nguoi choi khong dung duoc nua; khoa lai hoac mo lai khi da o trang thai do 409; ghi nhat ky")
    void lockAndUnlock() throws Exception {
        UUID id = createdId("LCK-1");
        mockMvc.perform(post(PATH + "/" + id + "/unlock").header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VOUCHER_INVALID"));
        mockMvc.perform(post(PATH + "/" + id + "/lock").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.status").value("locked")).andExpect(jsonPath("$.summary.state").value("locked"));
        mockMvc.perform(post(PATH + "/" + id + "/lock").header("Authorization", bearer(adminToken))).andExpect(status().isConflict());
        assertThat(audit("voucher.lock").getEntityId()).isEqualTo(id);

        User player = persistUser("player-locked", Role.PLAYER);
        mockMvc.perform(get("/api/v1/vouchers").header("Authorization", bearer(loginAs(player))))
                .andExpect(jsonPath("$[?(@.code=='LCK-1')]").isEmpty());
        mockMvc.perform(post(PATH + "/" + id + "/unlock").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.state").value("active"));
        assertThat(audit("voucher.unlock").getEntityId()).isEqualTo(id);
        mockMvc.perform(post(PATH + "/" + UUID.randomUUID() + "/lock").header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "/abc/lock").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH + "/" + UUID.randomUUID()).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Can quyen voucher.manage; chu san, nguoi choi 403; chua dang nhap 401; bi tu choi thi khong doi gi")
    void access() throws Exception {
        UUID id = createdId("ACC-1");
        String ownerToken = loginAs(persistOwner("o"));
        String playerToken = loginAs(persistUser("p", Role.PLAYER));
        for (String token : java.util.List.of(ownerToken, playerToken)) {
            mockMvc.perform(get(PATH).header("Authorization", bearer(token))).andExpect(status().isForbidden());
            mockMvc.perform(post(PATH).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(valid("NO-WAY"))).andExpect(status().isForbidden());
            mockMvc.perform(post(PATH + "/" + id + "/lock").header("Authorization", bearer(token))).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "voucher.manage".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        create(valid("NO-WAY-2")).andExpect(status().isForbidden());
        update(id, valid("ACC-1")).andExpect(status().isForbidden());
        mockMvc.perform(post(PATH + "/" + id + "/lock").header("Authorization", bearer(adminToken))).andExpect(status().isForbidden());
        assertThat(voucherRepository.findByCode("NO-WAY-2")).isEmpty();
        assertThat(voucherRepository.findById(id).orElseThrow().getStatus()).isEqualTo("active");
    }
}
