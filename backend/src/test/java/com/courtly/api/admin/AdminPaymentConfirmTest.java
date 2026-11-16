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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** 2.3.47 - Xac nhan chu san da thanh toan cong no; 2.3.50 - danh sach thanh toan. */
class AdminPaymentConfirmTest extends AdminFinanceTestBase {

    private static final String PATH = "/api/v1/admin/finance/commission-payments";
    private JsonNode confirm(UUID reportId, String body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(PATH + "/" + reportId + "/confirm").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Xac nhan du so tien ky: ky chuyen paid, moi hoa hong cua ky thanh settled, ghi nguoi duyet va audit")
    void confirmFullSettlement() throws Exception {
        CommissionSettlement settlement = settlement(owner, "30000", "0", "pending_review");
        BookingCommission a = commission(owner, venue, "due", "18000", 1, settlement);
        BookingCommission b = commission(owner, venue, "adjusted", "12000", 3, settlement);
        BookingCommission outside = commission(owner, venue, "due", "5000", 2, null);
        UUID reportId = report(owner, settlement, "30000");

        JsonNode result = confirm(reportId, "{\"note\":\"  Da doi chieu sao ke  \"}");
        assertThat(result.get("status").asText()).isEqualTo("confirmed");
        assertThat(result.get("reviewNote").asText()).isEqualTo("Da doi chieu sao ke");
        assertThat(result.get("reviewedByName").asText()).isEqualTo(admin.getFullName());
        assertThat(result.get("reviewedAt").asText()).isNotBlank();
        assertThat(result.get("courtlyOffsetAmount").decimalValue()).isEqualByComparingTo("0");

        assertThat(statusOf(a)).isEqualTo("settled");
        assertThat(statusOf(b)).isEqualTo("settled");
        assertThat(statusOf(outside)).isEqualTo("due");
        Map<String, Object> row = settlementRow(settlement);
        assertThat(row.get("status")).isEqualTo("paid");
        assertThat((BigDecimal) row.get("amount_paid")).isEqualByComparingTo("30000");
        assertThat(row.get("paid_at")).isNotNull();
        assertThat(row.get("reviewed_by")).isEqualTo(admin.getId());
        assertThat(jdbc.queryForObject("SELECT settled_at IS NOT NULL FROM booking_commissions WHERE id = ?", Boolean.class, a.getId())).isTrue();

        entityManager.flush();
        List<String> actions = jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_id IN (?, ?, ?, ?) ORDER BY action",
                String.class, reportId, settlement.getId(), a.getId(), b.getId());
        assertThat(actions).contains("settlement.payment.confirm", "commission.settle");
        assertThat(actions.stream().filter("commission.settle"::equals).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Thanh toan mot phan: chi dong cac hoa hong cu nhat da du tien, ky chuyen partial; tra not phan con lai thi paid")
    void confirmPartialThenRest() throws Exception {
        CommissionSettlement settlement = settlement(owner, "100000", "0", "pending_review");
        BookingCommission first = commission(owner, venue, "due", "60000", 1, settlement);
        BookingCommission second = commission(owner, venue, "due", "40000", 5, settlement);

        confirm(report(owner, settlement, "70000"), null);
        assertThat(settlementRow(settlement).get("status")).isEqualTo("partial");
        assertThat((BigDecimal) settlementRow(settlement).get("amount_paid")).isEqualByComparingTo("70000");
        assertThat(statusOf(first)).isEqualTo("settled");
        assertThat(statusOf(second)).isEqualTo("due");

        confirm(report(owner, settlement, "30000"), null);
        assertThat(settlementRow(settlement).get("status")).isEqualTo("paid");
        assertThat(statusOf(second)).isEqualTo("settled");
        assertThat(settlementRow(settlement).get("paid_at")).isNotNull();
    }

    @Test
    @DisplayName("Thanh toan khong gan ky: dong dan hoa hong cu nhat chua thuoc ky, giu so du khi chua du mot hoa hong")
    void confirmWithoutSettlementKeepsCredit() throws Exception {
        CommissionSettlement other = settlement(owner, "9000", "0", "open");
        BookingCommission inSettlement = commission(owner, venue, "due", "9000", 0, other);
        BookingCommission oldest = commission(owner, venue, "due", "18000", 1, null);
        BookingCommission middle = commission(owner, venue, "pending", "12000", 4, null);
        BookingCommission newest = commission(owner, venue, "adjusted", "30000", 8, null);

        confirm(report(owner, null, "20000"), null);
        assertThat(statusOf(oldest)).isEqualTo("settled");
        assertThat(statusOf(middle)).isEqualTo("pending");
        assertThat(credit(owner)).isEqualByComparingTo("2000");

        confirm(report(owner, null, "11000"), null);
        assertThat(statusOf(middle)).isEqualTo("settled");
        assertThat(statusOf(newest)).isEqualTo("adjusted");
        assertThat(credit(owner)).isEqualByComparingTo("1000");
        // Hoa hong da thuoc ky khac khong bi dong boi thanh toan khong gan ky.
        assertThat(statusOf(inSettlement)).isEqualTo("due");
        assertThat((BigDecimal) settlementRow(other).get("amount_paid")).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Bu tru khoan Courtly no chu san vao cong no con lai: chi bu tru phan vua du, bo qua khoan mien / tam hoan / da bu tru")
    void offsetsCourtlyOwed() throws Exception {
        CommissionSettlement settlement = settlement(owner, "30000", "0", "pending_review");
        commission(owner, venue, "due", "30000", 1, settlement);
        BookingCommission owedSmall = commission(owner, venue, "settled", "0", 1, null);
        BookingCommission owedBig = commission(owner, venue, "settled", "0", 1, null);
        BookingCommission owedWaived = commission(owner, venue, "waived", "0", 1, null);
        BookingCommission owedCleared = commission(owner, venue, "settled", "0", 1, null);
        jdbc.update("UPDATE booking_commissions SET courtly_owed = 5000, voucher_discount = 5000 WHERE id = ?", owedSmall.getId());
        jdbc.update("UPDATE booking_commissions SET courtly_owed = 9000, voucher_discount = 9000 WHERE id = ?", owedBig.getId());
        jdbc.update("UPDATE booking_commissions SET courtly_owed = 4000, voucher_discount = 4000 WHERE id = ?", owedWaived.getId());
        jdbc.update("UPDATE booking_commissions SET courtly_owed = 3000, voucher_discount = 3000, courtly_owed_cleared_at = now() WHERE id = ?", owedCleared.getId());

        // Chu san tra 22.000, con 8.000: chi khoan 5.000 vua trong han muc 8.000; khoan 9.000 vuot nen giu lai.
        JsonNode result = confirm(report(owner, settlement, "22000"), null);
        assertThat(result.get("courtlyOffsetAmount").decimalValue()).isEqualByComparingTo("5000");
        assertThat(clearedAt(owedSmall)).isNotNull();
        assertThat(clearedAt(owedBig)).isNull();
        assertThat(clearedAt(owedWaived)).isNull();
        // 22.000 + 5.000 = 27.000 chua du 30.000: ky chua paid.
        assertThat(settlementRow(settlement).get("status")).isEqualTo("partial");
        assertThat((BigDecimal) settlementRow(settlement).get("amount_paid")).isEqualByComparingTo("27000");

        // Tra not 3.000 (con lai) - khoan 9.000 van vuot nen khong bu tru, ky paid.
        JsonNode second = confirm(report(owner, settlement, "3000"), null);
        assertThat(second.get("courtlyOffsetAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(clearedAt(owedBig)).isNull();
        assertThat(settlementRow(settlement).get("status")).isEqualTo("paid");
    }

    @Test
    @DisplayName("Khoan Courtly no du lon de bu tru cho phan con lai thi ky paid du chu san tra it hon")
    void offsetCompletesSettlement() throws Exception {
        CommissionSettlement settlement = settlement(owner, "30000", "0", "pending_review");
        commission(owner, venue, "due", "30000", 1, settlement);
        BookingCommission owed = commission(owner, venue, "settled", "0", 1, null);
        jdbc.update("UPDATE booking_commissions SET courtly_owed = 8000, voucher_discount = 8000 WHERE id = ?", owed.getId());

        JsonNode result = confirm(report(owner, settlement, "22000"), null);
        assertThat(result.get("courtlyOffsetAmount").decimalValue()).isEqualByComparingTo("8000");
        assertThat(settlementRow(settlement).get("status")).isEqualTo("paid");
        assertThat((BigDecimal) settlementRow(settlement).get("amount_paid")).isEqualByComparingTo("30000");
        assertThat(clearedAt(owed)).isNotNull();
    }

    @Test
    @DisplayName("Sau khi xac nhan, han che cong no cua chu san duoc tinh lai va tu go khi khong con qua han")
    void liftsRestriction() throws Exception {
        CommissionSettlement settlement = settlement(owner, "18000", "0", "pending_review");
        BookingCommission overdue = commission(owner, venue, "due", "18000", -3, settlement);
        jdbc.update("UPDATE debt_restriction_levels SET enabled = true, min_overdue_amount = 10000, min_overdue_days = NULL WHERE level = 1");
        jdbc.update("UPDATE court_owner_profiles SET restriction_level = 1 WHERE user_id = ?", owner.getId());
        // Du lieu dat bang SQL: xoa bo nho dem JPA de service doc dung muc han che hien tai nhu mot request moi.
        entityManager.flush();
        entityManager.clear();

        confirm(report(owner, settlement, "18000"), null);
        assertThat(statusOf(overdue)).isEqualTo("settled");
        assertThat(jdbc.queryForObject("SELECT restriction_level FROM court_owner_profiles WHERE user_id = ?", Integer.class, owner.getId()))
                .isZero();
    }

    @Test
    @DisplayName("Khong xac nhan duoc: so tien vuot con no, bao cao da xu ly, ky da paid; id la 404; ghi chu qua dai 400")
    void rejectsInvalidConfirmations() throws Exception {
        CommissionSettlement settlement = settlement(owner, "20000", "5000", "partial");
        commission(owner, venue, "due", "20000", 1, settlement);
        UUID tooMuch = report(owner, settlement, "15001");
        mockMvc.perform(post(PATH + "/" + tooMuch + "/confirm").header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMISSION_PAYMENT_INVALID"));
        assertThat(jdbc.queryForObject("SELECT status FROM commission_payment_reports WHERE id = ?", String.class, tooMuch))
                .isEqualTo("pending_review");

        UUID ok = report(owner, settlement, "15000");
        confirm(ok, null);
        mockMvc.perform(post(PATH + "/" + ok + "/confirm").header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        // Ky da paid: bao cao pending con lai khong xac nhan duoc.
        mockMvc.perform(post(PATH + "/" + tooMuch + "/confirm").header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        assertThat((BigDecimal) settlementRow(settlement).get("amount_paid")).isEqualByComparingTo("20000");

        UUID rejected = report(owner, null, "1000");
        jdbc.update("UPDATE commission_payment_reports SET status = 'rejected' WHERE id = ?", rejected);
        mockMvc.perform(post(PATH + "/" + rejected + "/confirm").header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());

        mockMvc.perform(post(PATH + "/" + UUID.randomUUID() + "/confirm").header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "/khong-phai-uuid/confirm").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
        UUID fresh = report(owner, null, "1");
        mockMvc.perform(post(PATH + "/" + fresh + "/confirm").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Thanh toan khong gan ky vuot cong no chua thuoc ky (da tru so du): 409; vua du thi xac nhan duoc")
    void withoutSettlementCannotExceedDebt() throws Exception {
        commission(owner, venue, "due", "10000", 1, null);
        mockMvc.perform(post(PATH + "/" + report(owner, null, "10001") + "/confirm").header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        JsonNode ok = confirm(report(owner, null, "10000"), null);
        assertThat(ok.get("status").asText()).isEqualTo("confirmed");
        assertThat(credit(owner)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Danh sach: cho xac nhan len dau, dem theo trang thai, tong, loc chu san / ky / ngay / tu khoa, phan trang")
    void listAndFilters() throws Exception {
        CommissionSettlement settlement = settlement(owner, "30000", "0", "pending_review");
        UUID pending = report(owner, settlement, "10000");
        UUID confirmed = report(otherOwner, null, "5000");
        jdbc.update("UPDATE commission_payment_reports SET status = 'confirmed', paid_on = current_date - 30 WHERE id = ?", confirmed);
        UUID rejected = report(owner, null, "2000");
        jdbc.update("UPDATE commission_payment_reports SET status = 'rejected', review_note = 'Sai so tien' WHERE id = ?", rejected);

        JsonNode all = list("ownerId", owner.getId().toString());
        assertThat(all.get("content")).hasSize(2);
        assertThat(all.get("content").get(0).get("status").asText()).isEqualTo("pending_review");
        assertThat(all.get("content").get(0).get("settlement").get("status").asText()).isEqualTo("pending_review");
        assertThat(all.get("statusCounts").get("rejected").asLong()).isEqualTo(1);
        assertThat(all.get("totals").get("amount").decimalValue()).isEqualByComparingTo("12000");

        JsonNode everyone = list();
        assertThat(everyone.get("content").size()).isGreaterThanOrEqualTo(3);
        assertThat(list("status", "confirmed", "ownerId", otherOwner.getId().toString()).get("content")).hasSize(1);
        assertThat(list("settlementId", settlement.getId().toString()).get("content")).hasSize(1);
        assertThat(list("q", owner.getEmail().toUpperCase()).get("content")).hasSize(2);
        assertThat(list("q", "khong-ton-tai-xyz").get("content")).hasSize(0);
        assertThat(list("ownerId", otherOwner.getId().toString(), "to", LocalDate.now(VN).minusDays(10).toString()).get("content")).hasSize(1);
        assertThat(list("ownerId", otherOwner.getId().toString(), "from", LocalDate.now(VN).minusDays(10).toString()).get("content")).hasSize(0);
        JsonNode paged = list("ownerId", owner.getId().toString(), "size", "1", "page", "1");
        assertThat(paged.get("content")).hasSize(1);
        assertThat(paged.get("totalPages").asInt()).isEqualTo(2);
        assertThat(paged.get("totals").get("count").asLong()).isEqualTo(2);
        assertThat(pending).isNotNull();
    }

    private JsonNode list(String... params) throws Exception {
        var request = get(PATH).header("Authorization", bearer(adminToken));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Danh sach: tham so sai 400, chu san / ky khong ton tai 404")
    void listValidation() throws Exception {
        for (String[] bad : new String[][] {{"status", "khong-co"}, {"size", "0"}, {"size", "101"}, {"page", "-1"}, {"from", "2026-05-01", "to", "2026-04-01"}}) {
            var request = get(PATH).header("Authorization", bearer(adminToken));
            for (int i = 0; i < bad.length; i += 2) {
                request = request.param(bad[i], bad[i + 1]);
            }
            mockMvc.perform(request).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("ownerId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken)).param("settlementId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Xem can finance.commission.view, xac nhan can finance.commission.manage; chu san va nguoi choi 403; chua dang nhap 401")
    void access() throws Exception {
        CommissionSettlement settlement = settlement(owner, "10000", "0", "pending_review");
        commission(owner, venue, "due", "10000", 1, settlement);
        UUID reportId = report(owner, settlement, "10000");
        String confirmPath = PATH + "/" + reportId + "/confirm";
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(owner)))).andExpect(status().isForbidden());
        mockMvc.perform(post(confirmPath).header("Authorization", bearer(loginAs(owner)))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH).header("Authorization", bearer(player))).andExpect(status().isForbidden());
        mockMvc.perform(post(confirmPath).header("Authorization", bearer(player))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(confirmPath)).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "finance.commission.manage".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(get(PATH).header("Authorization", bearer(adminToken))).andExpect(status().isOk());
        mockMvc.perform(post(confirmPath).header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(jdbc.queryForObject("SELECT status FROM commission_payment_reports WHERE id = ?", String.class, reportId))
                .isEqualTo("pending_review");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_payment_reports WHERE status = 'pending_review' AND owner_id = ?",
                Long.class, owner.getId())).isEqualTo(1);
    }
}
