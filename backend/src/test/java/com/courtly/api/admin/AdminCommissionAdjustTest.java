package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.CommissionSettlement;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 2.3.51 - Dieu chinh cong no (hoa hong cua booking) co ly do va audit log. */
class AdminCommissionAdjustTest extends AdminFinanceTestBase {

    private static final String PATH = "/api/v1/admin/finance/commission-transactions/";

    private String body(String newAmount, String reason) {
        return "{" + (newAmount == null ? "" : "\"newAmount\":" + newAmount) + (newAmount != null && reason != null ? "," : "")
                + (reason == null ? "" : "\"reason\":\"" + reason + "\"") + "}";
    }

    private JsonNode adjust(BookingCommission commission, String newAmount, String reason) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(PATH + commission.getId() + "/adjustments").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body(newAmount, reason)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void expectStatus(BookingCommission commission, String payload, int expected) throws Exception {
        mockMvc.perform(post(PATH + commission.getId() + "/adjustments").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().is(expected));
    }

    private BigDecimal amountOf(BookingCommission commission) {
        return jdbc.queryForObject("SELECT commission_amount FROM booking_commissions WHERE id = ?", BigDecimal.class, commission.getId());
    }

    @Test
    @DisplayName("Giam hoa hong thuoc ky: hoa hong chuyen adjusted, ky doi phai thu va tong dieu chinh, tra ve chi tiet moi va audit co ly do")
    void reduceInsideSettlement() throws Exception {
        CommissionSettlement settlement = settlement(owner, "30000", "0", "open");
        BookingCommission a = commission(owner, venue, "due", "18000", 3, settlement);
        BookingCommission b = commission(owner, venue, "due", "12000", 3, settlement);

        JsonNode detail = adjust(a, "10000", "  Khach hang khieu nai, giam hoa hong  ");
        assertThat(detail.get("commission").get("commissionAmount").decimalValue()).isEqualByComparingTo("10000");
        assertThat(detail.get("commission").get("status").asText()).isEqualTo("adjusted");
        assertThat(detail.get("history")).hasSize(1);
        assertThat(detail.get("history").get(0).get("action").asText()).isEqualTo("commission.adjust");
        assertThat(detail.get("history").get(0).get("newData").get("reason").asText()).isEqualTo("Khach hang khieu nai, giam hoa hong");
        assertThat(detail.get("history").get(0).get("oldData").get("commissionAmount").asText()).isEqualTo("18000");
        assertThat(detail.get("settlement").get("amountDue").decimalValue()).isEqualByComparingTo("22000");

        Map<String, Object> row = jdbc.queryForMap("SELECT amount_due, adjustment_total, commission_total, status FROM commission_settlements WHERE id = ?",
                settlement.getId());
        assertThat((BigDecimal) row.get("amount_due")).isEqualByComparingTo("22000");
        assertThat((BigDecimal) row.get("adjustment_total")).isEqualByComparingTo("-8000");
        assertThat((BigDecimal) row.get("commission_total")).isEqualByComparingTo("30000");
        assertThat(row.get("status")).isEqualTo("open");
        assertThat(amountOf(b)).isEqualByComparingTo("12000");

        entityManager.flush();
        List<String> actions = jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_id IN (?, ?) ORDER BY action", String.class,
                a.getId(), settlement.getId());
        assertThat(actions).containsExactlyInAnyOrder("commission.adjust", "settlement.adjust");
        assertThat(jdbc.queryForObject("SELECT new_data ->> 'reason' FROM audit_logs WHERE entity_id = ? AND action = 'settlement.adjust'",
                String.class, settlement.getId())).isEqualTo("Khach hang khieu nai, giam hoa hong");
    }

    @Test
    @DisplayName("Tang hoa hong: so phai thu cua ky tang theo, tong dieu chinh duong; dieu chinh nhieu lan cong don")
    void increaseAndAccumulate() throws Exception {
        CommissionSettlement settlement = settlement(owner, "20000", "0", "open");
        BookingCommission a = commission(owner, venue, "due", "20000", 3, settlement);
        adjust(a, "26000", "Bo sung phi dich vu");
        adjust(a, "23000", "Dieu chinh lai sau doi chieu");
        Map<String, Object> row = jdbc.queryForMap("SELECT amount_due, adjustment_total FROM commission_settlements WHERE id = ?", settlement.getId());
        assertThat((BigDecimal) row.get("amount_due")).isEqualByComparingTo("23000");
        assertThat((BigDecimal) row.get("adjustment_total")).isEqualByComparingTo("3000");
        JsonNode detail = objectMapper.readTree(mockMvc.perform(get(PATH + a.getId()).header("Authorization", bearer(adminToken)))
                .andReturn().getResponse().getContentAsString());
        assertThat(detail.get("history")).hasSize(2);
        assertThat(detail.get("history").get(0).get("newData").get("commissionAmount").asText()).isEqualTo("23000");
    }

    @Test
    @DisplayName("Giam den dung so da thu: ky chuyen paid va cac hoa hong con lai thanh settled")
    void reachingPaidSettlesPeriod() throws Exception {
        CommissionSettlement settlement = settlement(owner, "30000", "20000", "partial");
        BookingCommission a = commission(owner, venue, "due", "18000", 1, settlement);
        BookingCommission b = commission(owner, venue, "due", "12000", 5, settlement);
        adjust(a, "8000", "Mien giam phan con lai");
        Map<String, Object> row = settlementRow(settlement);
        assertThat(row.get("status")).isEqualTo("paid");
        assertThat((BigDecimal) row.get("amount_paid")).isEqualByComparingTo("20000");
        assertThat(row.get("paid_at")).isNotNull();
        assertThat(statusOf(a)).isEqualTo("settled");
        assertThat(statusOf(b)).isEqualTo("settled");
    }

    @Test
    @DisplayName("Giam nhung chua den muc da thu: hoa hong du tien da thu duoc dong, phan con lai con no")
    void partialCoverageAfterReduction() throws Exception {
        CommissionSettlement settlement = settlement(owner, "40000", "15000", "partial");
        BookingCommission a = commission(owner, venue, "due", "25000", 1, settlement);
        BookingCommission b = commission(owner, venue, "due", "15000", 5, settlement);
        adjust(a, "10000", "Giam hoa hong theo thoa thuan");
        assertThat((BigDecimal) settlementRow(settlement).get("amount_due")).isEqualByComparingTo("25000");
        assertThat(settlementRow(settlement).get("status")).isEqualTo("partial");
        assertThat(statusOf(a)).isEqualTo("settled");
        assertThat(statusOf(b)).isEqualTo("due");
    }

    @Test
    @DisplayName("Khong de so phai thu thap hon so da thu: 409 va khong doi gi")
    void cannotGoBelowPaid() throws Exception {
        CommissionSettlement settlement = settlement(owner, "30000", "20000", "partial");
        BookingCommission a = commission(owner, venue, "due", "30000", 3, settlement);
        expectStatus(a, body("9999", "Giam qua muc da thu"), 409);
        assertThat(amountOf(a)).isEqualByComparingTo("30000");
        assertThat(statusOf(a)).isEqualTo("due");
        assertThat((BigDecimal) settlementRow(settlement).get("amount_due")).isEqualByComparingTo("30000");
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id = ?", Long.class, a.getId())).isZero();
    }

    @Test
    @DisplayName("Hoa hong khong thuoc ky: doi so tien, so du chu san co the dong luon hoa hong vua giam")
    void withoutSettlementUsesCredit() throws Exception {
        BookingCommission oldest = commission(owner, venue, "due", "8000", 1, null);
        BookingCommission later = commission(owner, venue, "due", "20000", 5, null);
        jdbc.update("UPDATE court_owner_profiles SET commission_credit = 5000 WHERE user_id = ?", owner.getId());
        entityManager.flush();
        entityManager.clear();

        JsonNode detail = adjust(oldest, "4000", "Giam hoa hong do loi he thong");
        assertThat(detail.get("settlement")).isNull();
        assertThat(statusOf(oldest)).isEqualTo("settled");
        assertThat(statusOf(later)).isEqualTo("due");
        assertThat(jdbc.queryForObject("SELECT commission_credit FROM court_owner_profiles WHERE user_id = ?", BigDecimal.class, owner.getId()))
                .isEqualByComparingTo("1000");
        assertThat(detail.get("history").get(0).get("newData").get("status").asText()).isEqualTo("settled");
    }

    @Test
    @DisplayName("Dieu chinh ve 0 hop le; khong dung toi phan voucher da chot cua hoa hong")
    void zeroAndVoucherFields() throws Exception {
        BookingCommission a = commission(owner, venue, "pending", "9000", 5, null);
        jdbc.update("UPDATE booking_commissions SET voucher_discount = 3000, courtly_owed = 0 WHERE id = ?", a.getId());
        entityManager.flush();
        entityManager.clear();
        JsonNode detail = adjust(a, "0", "Mien hoa hong cho booking nay");
        // So tien 0 khong co gi de dong tu so du 0 nhung 0 <= 0 nen duoc dong luon.
        assertThat(amountOf(a)).isEqualByComparingTo("0");
        assertThat(detail.get("commission").get("voucherDiscount").decimalValue()).isEqualByComparingTo("3000");
    }

    @Test
    @DisplayName("Tinh lai han che cong no sau khi giam hoa hong qua han")
    void recomputesRestriction() throws Exception {
        CommissionSettlement settlement = settlement(owner, "18000", "0", "overdue");
        BookingCommission a = commission(owner, venue, "due", "18000", -3, settlement);
        jdbc.update("UPDATE debt_restriction_levels SET enabled = true, min_overdue_amount = 10000, min_overdue_days = NULL WHERE level = 1");
        jdbc.update("UPDATE court_owner_profiles SET restriction_level = 1 WHERE user_id = ?", owner.getId());
        entityManager.flush();
        entityManager.clear();
        adjust(a, "5000", "Giam xuong duoi nguong han che");
        assertThat(jdbc.queryForObject("SELECT restriction_level FROM court_owner_profiles WHERE user_id = ?", Integer.class, owner.getId())).isZero();
    }

    @Test
    @DisplayName("Chi dieu chinh hoa hong chua thu: settled, waived, suspended bi tu choi 409")
    void onlyOutstandingCommissions() throws Exception {
        for (String state : new String[] {"settled", "waived", "suspended"}) {
            BookingCommission commission = commission(owner, venue, state, "10000", 3, null);
            expectStatus(commission, body("5000", "Dieu chinh khong hop le"), 409);
            assertThat(amountOf(commission)).isEqualByComparingTo("10000");
        }
    }

    @Test
    @DisplayName("Kiem tra du lieu: thieu so tien / ly do, so am, so le, qua lon, ly do 5-500 ky tu, cung so tien hien tai; id 404 / sai dinh dang 400")
    void validation() throws Exception {
        BookingCommission a = commission(owner, venue, "due", "10000", 3, null);
        String[] bad = {
                "{}", body("5000", null), body(null, "Ly do hop le"), body("-1", "Ly do hop le"), body("1000.5", "Ly do hop le"),
                body("99999999999", "Ly do hop le"), body("5000", ""), body("5000", "    "), body("5000", "ngan"),
                body("5000", "x".repeat(501)), body("10000", "Cung so tien hien tai"),
                "{\"newAmount\":\"abc\",\"reason\":\"Ly do hop le\"}",
        };
        for (String payload : bad) {
            expectStatus(a, payload, 400);
        }
        assertThat(amountOf(a)).isEqualByComparingTo("10000");
        mockMvc.perform(post(PATH + UUID.randomUUID() + "/adjustments").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(body("5000", "Khong ton tai"))).andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "khong-phai-uuid/adjustments").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(body("5000", "Sai dinh dang"))).andExpect(status().isBadRequest());
        // Ly do dung 500 ky tu hop le.
        adjust(a, "5000", "y".repeat(500));
    }

    @Test
    @DisplayName("Chi tiet doi soat hien thi lich su dieu chinh moi nhat truoc; can finance.commission.manage; 403 / 401")
    void historyAndAccess() throws Exception {
        BookingCommission a = commission(owner, venue, "due", "10000", 3, null);
        adjust(a, "9000", "Lan dieu chinh thu nhat");
        adjust(a, "7000", "Lan dieu chinh thu hai");
        mockMvc.perform(get(PATH + a.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.history", hasSize(2)))
                .andExpect(jsonPath("$.history[0].newData.reason").value("Lan dieu chinh thu hai"))
                .andExpect(jsonPath("$.history[1].newData.reason").value("Lan dieu chinh thu nhat"));

        String path = PATH + a.getId() + "/adjustments";
        String payload = body("1000", "Khong du quyen");
        for (String token : new String[] {loginAs(owner), player}) {
            mockMvc.perform(post(path).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isUnauthorized());
        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "finance.commission.manage".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(post(path).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(amountOf(a)).isEqualByComparingTo("7000");
    }
}
