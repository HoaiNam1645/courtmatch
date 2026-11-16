package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** 2.3.45 - Xem danh sach ky cong no hoa hong va chot ky; 2.3.46 - Xem chi tiet cong no. */
class AdminSettlementTest extends AdminApiTestBase {

    private static final String PATH = "/api/v1/admin/finance/settlements";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingCommissionRepository commissionRepository;
    @Autowired private CommissionSettlementRepository settlementRepository;
    @Autowired private JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    private String adminToken;
    private User owner;
    private User otherOwner;
    private Venue venue;
    private Venue otherVenue;
    private String player;
    private int index;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        adminToken = loginAs(persistAdmin("admin-settlement"));
        owner = persistOwner("owner-settlement");
        otherOwner = persistOwner("owner-settlement-2");
        venue = persistVenue(owner, "San Ky Mot");
        otherVenue = persistVenue(otherOwner, "San Ky Hai");
        player = loginAs(persistUser("player-settlement", Role.PLAYER));
    }

    private BookingCommission commission(Venue target, User commissionOwner, String status, String amount, String calculatedOn,
                                         CommissionSettlement settlement, Instant dueAt) throws Exception {
        String id = createBooking(player, target.getCourts().getFirst(), nextMonday().plusDays(index++), LocalTime.of(9, 0));
        Booking booking = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
        BookingCommission commission = new BookingCommission();
        commission.setId(UUID.randomUUID());
        commission.setBooking(booking);
        commission.setOwner(commissionOwner);
        commission.setGrossAmount(new BigDecimal("180000"));
        commission.setFeeType(FeeType.PERCENTAGE);
        commission.setFeeValue(BigDecimal.TEN);
        commission.setCommissionAmount(new BigDecimal(amount));
        commission.setStatus(status);
        Instant calculated = ZonedDateTime.of(LocalDate.parse(calculatedOn), LocalTime.of(10, 0), VN).toInstant();
        commission.setCalculatedAt(calculated);
        commission.setDueAt(dueAt != null ? dueAt : calculated.plus(7, ChronoUnit.DAYS));
        commission.setCreatedAt(calculated);
        commission.setSettlement(settlement);
        return commissionRepository.saveAndFlush(commission);
    }

    private CommissionSettlement settlement(User settlementOwner, String from, String to, String status, String due, String paid) {
        CommissionSettlement settlement = new CommissionSettlement();
        settlement.setId(UUID.randomUUID());
        settlement.setOwner(settlementOwner);
        settlement.setPeriodFrom(LocalDate.parse(from));
        settlement.setPeriodTo(LocalDate.parse(to));
        settlement.setStatus(status);
        settlement.setCommissionTotal(new BigDecimal(due));
        settlement.setAmountDue(new BigDecimal(due));
        settlement.setAmountPaid(new BigDecimal(paid));
        return settlementRepository.saveAndFlush(settlement);
    }

    private JsonNode list(String... params) throws Exception {
        var request = get(PATH).header("Authorization", bearer(adminToken));
        java.util.List<String> given = java.util.Arrays.asList(params);
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

    private void sample() throws Exception {
        CommissionSettlement a = settlement(owner, "2020-03-01", "2020-03-07", "pending_review", "100000", "0");
        CommissionSettlement b = settlement(owner, "2020-03-08", "2020-03-14", "partial", "80000", "30000");
        CommissionSettlement c = settlement(otherOwner, "2020-04-01", "2020-04-07", "paid", "50000", "50000");
        CommissionSettlement d = settlement(otherOwner, "2020-05-01", "2020-05-07", "open", "20000", "0");
        commission(venue, owner, "due", "100000", "2020-03-02", a, Instant.now().minus(2, ChronoUnit.DAYS));
        commission(venue, owner, "due", "80000", "2020-03-09", b, Instant.now().plus(3, ChronoUnit.DAYS));
        commission(otherVenue, otherOwner, "settled", "50000", "2020-04-02", c, null);
        commission(otherVenue, otherOwner, "pending", "20000", "2020-05-02", d, Instant.now().plus(5, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("Moi dong day du: chu san, ky, hoa hong, phai thu, da thu, con lai, trang thai, so booking, qua han")
    void rowsAreComplete() throws Exception {
        sample();
        JsonNode body = list();
        assertThat(body.get("content")).hasSize(4);
        JsonNode first = body.get("content").get(0);
        // Ky cho Courtly xac nhan dung dau danh sach.
        assertThat(first.get("status").asText()).isEqualTo("pending_review");
        assertThat(first.get("ownerName").asText()).isNotBlank();
        assertThat(first.get("ownerEmail").asText()).contains("@");
        assertThat(first.get("amountDue").decimalValue()).isEqualByComparingTo("100000");
        assertThat(first.get("remaining").decimalValue()).isEqualByComparingTo("100000");
        assertThat(first.get("bookingCount").asLong()).isEqualTo(1);
        assertThat(first.get("overdue").asBoolean()).isTrue();
        JsonNode partial = find(body, "partial");
        assertThat(partial.get("amountPaid").decimalValue()).isEqualByComparingTo("30000");
        assertThat(partial.get("remaining").decimalValue()).isEqualByComparingTo("50000");
        assertThat(partial.get("overdue").asBoolean()).isFalse();
        JsonNode paid = find(body, "paid");
        assertThat(paid.get("remaining").decimalValue()).isEqualByComparingTo("0");
        assertThat(paid.get("overdue").asBoolean()).isFalse();
    }

    private static JsonNode find(JsonNode body, String status) {
        for (JsonNode row : body.get("content")) {
            if (status.equals(row.get("status").asText())) {
                return row;
            }
        }
        throw new AssertionError("Khong co ky o trang thai " + status);
    }

    @Test
    @DisplayName("Tong cua tap loc va so luong theo trang thai khong phu thuoc trang; dem trang thai bo qua bo loc trang thai")
    void totalsAndStatusCounts() throws Exception {
        sample();
        JsonNode all = list();
        assertThat(all.get("totals").get("count").asLong()).isEqualTo(4);
        assertThat(all.get("totals").get("amountDue").decimalValue()).isEqualByComparingTo("250000");
        assertThat(all.get("totals").get("amountPaid").decimalValue()).isEqualByComparingTo("80000");
        assertThat(all.get("totals").get("remaining").decimalValue()).isEqualByComparingTo("170000");
        assertThat(all.get("statusCounts").get("pending_review").asLong()).isEqualTo(1);
        assertThat(all.get("statusCounts").get("overdue").asLong()).isZero();

        JsonNode paged = list("size", "1", "page", "2");
        assertThat(paged.get("content")).hasSize(1);
        assertThat(paged.get("totalPages").asInt()).isEqualTo(4);
        assertThat(paged.get("totals").get("amountDue").decimalValue()).isEqualByComparingTo("250000");

        JsonNode filtered = list("status", "partial");
        assertThat(filtered.get("content")).hasSize(1);
        assertThat(filtered.get("totals").get("amountDue").decimalValue()).isEqualByComparingTo("80000");
        // So luong trang thai van la cua toan bo tap, de chuyen tab khong mat so.
        assertThat(filtered.get("statusCounts").get("pending_review").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("Loc theo chu san, tim theo ten/email, khoang ky, chi ky qua han")
    void filters() throws Exception {
        sample();
        assertThat(list("ownerId", otherOwner.getId().toString()).get("content")).hasSize(2);
        assertThat(list("q", otherOwner.getEmail().toUpperCase()).get("content")).hasSize(2);
        // Ten chu san va chi mot chu san trong ket qua khi tim bang email day du cua chu san con lai.
        assertThat(list("q", owner.getEmail()).get("content")).hasSize(2);
        assertThat(list("q", "khong-ton-tai-xyz").get("content")).hasSize(0);
        assertThat(list("from", "2020-04-01", "to", "2020-04-30").get("content")).hasSize(1);
        // Ky giao khoang loc dau/cuoi van duoc tinh.
        assertThat(list("from", "2020-03-07", "to", "2020-03-08").get("content")).hasSize(2);
        JsonNode overdue = list("overdue", "true");
        assertThat(overdue.get("content")).hasSize(1);
        assertThat(overdue.get("content").get(0).get("status").asText()).isEqualTo("pending_review");
        // Ky ky tu hoa hong da thanh toan khong bi coi la qua han du han da qua.
        assertThat(list("status", "paid", "overdue", "true").get("content")).hasSize(0);
    }

    @Test
    @DisplayName("Tham so khong hop le tra 400; chu san khong ton tai 404")
    void listValidation() throws Exception {
        for (String[] bad : new String[][] {{"status", "khong-co"}, {"size", "0"}, {"size", "101"}, {"page", "-1"}, {"from", "2020-05-01", "to", "2020-04-01"}}) {
            var request = get(PATH).header("Authorization", bearer(adminToken));
            for (int i = 0; i < bad.length; i += 2) {
                request = request.param(bad[i], bad[i + 1]);
            }
            mockMvc.perform(request).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("ownerId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    private String body(UUID ownerId, String from, String to, String note) {
        return "{\"ownerId\":\"" + ownerId + "\",\"periodFrom\":\"" + from + "\",\"periodTo\":\"" + to + "\""
                + (note == null ? "" : ",\"note\":\"" + note + "\"") + "}";
    }

    @Test
    @DisplayName("Chot ky: chi gom hoa hong chua doi soat cua dung chu san trong khoang ngay, tinh dung so tien, gan settlement_id")
    void createGathersOnlyEligibleCommissions() throws Exception {
        LocalDate day = LocalDate.now(VN).minusDays(10);
        String in = day.toString();
        BookingCommission one = commission(venue, owner, "due", "18000", in, null, null);
        BookingCommission two = commission(venue, owner, "pending", "12000", in, null, null);
        BookingCommission three = commission(venue, owner, "adjusted", "5000", in, null, null);
        BookingCommission suspended = commission(venue, owner, "suspended", "7000", in, null, null);
        BookingCommission settled = commission(venue, owner, "settled", "9000", in, null, null);
        BookingCommission waived = commission(venue, owner, "waived", "4000", in, null, null);
        BookingCommission outsideDate = commission(venue, owner, "due", "3000", day.minusDays(5).toString(), null, null);
        BookingCommission otherOwnerOne = commission(otherVenue, otherOwner, "due", "6000", in, null, null);
        CommissionSettlement existing = settlement(owner, "2020-01-01", "2020-01-07", "open", "1000", "0");
        BookingCommission alreadyAssigned = commission(venue, owner, "due", "2000", in, existing, null);

        String from = day.minusDays(1).toString();
        String to = day.plusDays(1).toString();
        mockMvc.perform(post(PATH).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(owner.getId(), from, to, "  Ky tuan truoc  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.bookingCount").value(3))
                .andExpect(jsonPath("$.commissionTotal").value(35000))
                .andExpect(jsonPath("$.amountDue").value(35000))
                .andExpect(jsonPath("$.amountPaid").value(0))
                .andExpect(jsonPath("$.remaining").value(35000));

        UUID settlementId = jdbc.queryForObject("SELECT id FROM commission_settlements WHERE owner_id = ? AND period_from = ?::date",
                UUID.class, owner.getId(), from);
        for (BookingCommission gathered : new BookingCommission[] {one, two, three}) {
            assertThat(settlementOf(gathered)).isEqualTo(settlementId);
        }
        for (BookingCommission skipped : new BookingCommission[] {suspended, settled, waived, outsideDate, otherOwnerOne}) {
            assertThat(settlementOf(skipped)).isNull();
        }
        assertThat(settlementOf(alreadyAssigned)).isEqualTo(existing.getId());
        assertThat(jdbc.queryForObject("SELECT note FROM commission_settlements WHERE id = ?", String.class, settlementId))
                .isEqualTo("Ky tuan truoc");

        entityManager.flush();
        Map<String, Object> audit = jdbc.queryForMap("SELECT action, entity_type, new_data::text AS data FROM audit_logs WHERE entity_id = ?", settlementId);
        assertThat(audit.get("action")).isEqualTo("settlement.create");
        assertThat(audit.get("entity_type")).isEqualTo("commission_settlement");
        assertThat((String) audit.get("data")).contains("35000");
    }

    private UUID settlementOf(BookingCommission commission) {
        return jdbc.queryForObject("SELECT settlement_id FROM booking_commissions WHERE id = ?", UUID.class, commission.getId());
    }

    @Test
    @DisplayName("Chot ky lan hai cung khoang: 409, khong co hoa hong nao: 409; hoa hong da vao ky khong bi gom lai")
    void createDuplicateAndEmpty() throws Exception {
        String day = LocalDate.now(VN).minusDays(3).toString();
        commission(venue, owner, "due", "18000", day, null, null);
        String payload = body(owner.getId(), day, day, null);
        mockMvc.perform(post(PATH).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());
        // Hoa hong da duoc gan vao ky dau nen khong con gi de gom.
        mockMvc.perform(post(PATH).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMISSION_PAYMENT_INVALID"));
        // Mot chu san khac cung khoang ngay nhung khong co hoa hong.
        mockMvc.perform(post(PATH).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(otherOwner.getId(), day, day, null)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_settlements WHERE owner_id = ?", Long.class, owner.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Chot ky kiem tra du lieu: thieu truong, ngay dao nguoc, ngay tuong lai, qua xa, ghi chu dai; khong phai chu san 404")
    void createValidation() throws Exception {
        String today = LocalDate.now(VN).toString();
        String tomorrow = LocalDate.now(VN).plusDays(1).toString();
        String[] bad = {
                "{}",
                "{\"ownerId\":\"" + owner.getId() + "\"}",
                body(owner.getId(), today, LocalDate.now(VN).minusDays(1).toString(), null),
                body(owner.getId(), today, tomorrow, null),
                body(owner.getId(), LocalDate.now(VN).minusYears(3).toString(), today, null),
                body(owner.getId(), today, today, "x".repeat(501)),
                "{\"ownerId\":\"khong-phai-uuid\",\"periodFrom\":\"" + today + "\",\"periodTo\":\"" + today + "\"}",
        };
        for (String payload : bad) {
            mockMvc.perform(post(PATH).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post(PATH).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID(), today, today, null)))
                .andExpect(status().isNotFound());
        UUID playerId = jdbc.queryForObject("SELECT id FROM users WHERE email LIKE 'player-settlement%'", UUID.class);
        mockMvc.perform(post(PATH).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(playerId, today, today, null)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_settlements WHERE owner_id = ?", Long.class, owner.getId())).isZero();
    }

    @Test
    @DisplayName("Chi tiet ky: so tien, chu san, han che, khoan Courtly no, bao cao thanh toan, booking cau thanh, lich su")
    void detail() throws Exception {
        CommissionSettlement settlement = settlement(owner, "2020-03-01", "2020-03-07", "pending_review", "28000", "0");
        BookingCommission late = commission(venue, owner, "due", "18000", "2020-03-02", settlement, Instant.now().minus(1, ChronoUnit.DAYS));
        commission(venue, owner, "adjusted", "10000", "2020-03-03", settlement, Instant.now().plus(2, ChronoUnit.DAYS));
        jdbc.update("UPDATE booking_commissions SET voucher_discount = 40000, courtly_owed = 22000 WHERE id = ?", late.getId());
        jdbc.update("UPDATE court_owner_profiles SET restriction_level = 1 WHERE user_id = ?", owner.getId());
        jdbc.update("INSERT INTO commission_payment_reports (id, owner_id, settlement_id, amount, paid_on, reference_code, status) "
                + "VALUES (gen_random_uuid(), ?, ?, 28000, '2020-03-10', 'REF-DETAIL', 'pending_review')", owner.getId(), settlement.getId());
        jdbc.update("INSERT INTO audit_logs (id, actor_id, action, entity_type, entity_id, new_data, created_at) "
                + "VALUES (gen_random_uuid(), ?, 'settlement.create', 'commission_settlement', ?, '{\"bookings\":2}'::jsonb, now())",
                owner.getId(), settlement.getId());

        mockMvc.perform(get(PATH + "/" + settlement.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.id").value(settlement.getId().toString()))
                .andExpect(jsonPath("$.settlement.status").value("pending_review"))
                .andExpect(jsonPath("$.settlement.amountDue").value(28000))
                .andExpect(jsonPath("$.settlement.pendingReports").value(1))
                .andExpect(jsonPath("$.settlement.bookingCount").value(2))
                .andExpect(jsonPath("$.settlement.ownerEmail").value(owner.getEmail()))
                .andExpect(jsonPath("$.restrictionLevel").value(1))
                .andExpect(jsonPath("$.owedByCourtly").value(22000))
                .andExpect(jsonPath("$.reports", hasSize(1)))
                .andExpect(jsonPath("$.reports[0].referenceCode").value("REF-DETAIL"))
                .andExpect(jsonPath("$.reports[0].status").value("pending_review"))
                .andExpect(jsonPath("$.bookings.totalElements").value(2))
                .andExpect(jsonPath("$.bookings.content", hasSize(2)))
                .andExpect(jsonPath("$.bookings.content[0].overdue").value(true))
                .andExpect(jsonPath("$.bookings.content[0].commissionAmount").value(18000))
                .andExpect(jsonPath("$.bookings.content[1].overdue").value(false))
                .andExpect(jsonPath("$.history", hasSize(1)))
                .andExpect(jsonPath("$.history[0].action").value("settlement.create"));

        mockMvc.perform(get(PATH + "/" + settlement.getId()).header("Authorization", bearer(adminToken)).param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings.content", hasSize(1)))
                .andExpect(jsonPath("$.bookings.totalPages").value(2));
    }

    @Test
    @DisplayName("Chi tiet: id khong ton tai 404, id sai dinh dang 400; ky khong co booking van mo duoc")
    void detailNotFoundAndEmpty() throws Exception {
        mockMvc.perform(get(PATH + "/" + UUID.randomUUID()).header("Authorization", bearer(adminToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/khong-phai-uuid").header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        CommissionSettlement empty = settlement(owner, "2020-06-01", "2020-06-07", "open", "0", "0");
        mockMvc.perform(get(PATH + "/" + empty.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings.content", hasSize(0)))
                .andExpect(jsonPath("$.reports", hasSize(0)))
                .andExpect(jsonPath("$.history", hasSize(0)))
                .andExpect(jsonPath("$.owedByCourtly").value(0));
    }

    @Test
    @DisplayName("Xem can finance.commission.view, chot ky can finance.commission.manage; chu san, nguoi choi 403; chua dang nhap 401")
    void access() throws Exception {
        String day = LocalDate.now(VN).minusDays(2).toString();
        commission(venue, owner, "due", "18000", day, null, null);
        String payload = body(owner.getId(), day, day, null);
        CommissionSettlement settlement = settlement(owner, "2020-03-01", "2020-03-07", "open", "1000", "0");
        for (String path : new String[] {PATH, PATH + "/" + settlement.getId()}) {
            mockMvc.perform(get(path).header("Authorization", bearer(loginAs(owner)))).andExpect(status().isForbidden());
            mockMvc.perform(get(path).header("Authorization", bearer(player))).andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post(PATH).header("Authorization", bearer(loginAs(owner))).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isUnauthorized());

        // Co quyen xem nhung khong co quyen xu ly: xem duoc, chot ky bi tu choi.
        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "finance.commission.manage".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken))).andExpect(status().isOk());
        mockMvc.perform(post(PATH).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        adminRole.getRolePermissions().removeIf(item -> "finance.commission.view".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_settlements WHERE owner_id = ? AND period_from = ?::date",
                Long.class, owner.getId(), day)).isZero();
    }
}
