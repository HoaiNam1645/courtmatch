package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
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

/** 2.3.52 - Ghi nhan hoan tien truc tiep, dieu chinh hoa hong cua booking theo phan hoan. */
class AdminRefundTest extends AdminFinanceTestBase {

    private static final String PATH = "/api/v1/admin/finance/commission-transactions/";

    private String body(String amount, String reason) {
        return "{" + (amount == null ? "" : "\"amount\":" + amount) + (amount != null && reason != null ? "," : "")
                + (reason == null ? "" : "\"reason\":\"" + reason + "\"") + "}";
    }

    private JsonNode refund(BookingCommission commission, String amount, String reason) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(PATH + commission.getId() + "/refunds").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body(amount, reason)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void expectStatus(BookingCommission commission, String payload, int expected) throws Exception {
        mockMvc.perform(post(PATH + commission.getId() + "/refunds").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().is(expected));
    }

    private Map<String, Object> commissionRow(BookingCommission commission) {
        return jdbc.queryForMap("SELECT commission_amount, voucher_discount, courtly_owed, status FROM booking_commissions WHERE id = ?", commission.getId());
    }

    private String paymentStatus(UUID paymentId) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, paymentId);
    }

    private BigDecimal credit() {
        return jdbc.queryForObject("SELECT commission_credit FROM court_owner_profiles WHERE user_id = ?", BigDecimal.class, owner.getId());
    }

    @Test
    @DisplayName("Hoan mot phan: hoa hong giam theo ty le, ghi hoan da xac nhan, thanh toan partially_refunded, audit va chi tiet tra ve co khoan hoan")
    void partialRefund() throws Exception {
        BookingCommission commission = commission(owner, venue, "due", "18000", 3, null);
        UUID paymentId = payment(commission, "confirmed", null);

        JsonNode detail = refund(commission, "90000", "  San ngung hoat dong giua chung  ");
        assertThat(commissionRow(commission).get("commission_amount")).isEqualTo(new BigDecimal("9000.00"));
        assertThat(commissionRow(commission).get("status")).isEqualTo("adjusted");
        assertThat(paymentStatus(paymentId)).isEqualTo("partially_refunded");
        assertThat(detail.get("refunds")).hasSize(1);
        assertThat(detail.get("refunds").get(0).get("amount").decimalValue()).isEqualByComparingTo("90000");
        assertThat(detail.get("refunds").get(0).get("status").asText()).isEqualTo("confirmed");
        assertThat(detail.get("refunds").get(0).get("reason").asText()).isEqualTo("San ngung hoat dong giua chung");
        assertThat(detail.get("commission").get("commissionAmount").decimalValue()).isEqualByComparingTo("9000");
        assertThat(detail.get("history").get(0).get("action").asText()).isEqualTo("commission.refund_adjust");
        assertThat(detail.get("history").get(0).get("newData").get("reason").asText()).isEqualTo("San ngung hoat dong giua chung");

        entityManager.flush();
        List<String> actions = jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_id IN (?, ?) OR entity_id IN (SELECT id FROM refunds WHERE payment_id = ?)",
                String.class, commission.getId(), paymentId, paymentId);
        assertThat(actions).contains("commission.refund_adjust", "refund.record");
    }

    @Test
    @DisplayName("Hoan nhieu lan: ty le tinh theo phan con lai cua khoan da thu; hoan het thi hoa hong waived bang 0 va thanh toan refunded")
    void cumulativeThenFull() throws Exception {
        BookingCommission commission = commission(owner, venue, "due", "18000", 3, null);
        UUID paymentId = payment(commission, "confirmed", null);
        refund(commission, "90000", "Hoan lan thu nhat");
        refund(commission, "45000", "Hoan lan thu hai");
        // Con lai 45.000/180.000 = 25% -> 4.500.
        assertThat((BigDecimal) commissionRow(commission).get("commission_amount")).isEqualByComparingTo("4500");
        assertThat(paymentStatus(paymentId)).isEqualTo("partially_refunded");

        JsonNode detail = refund(commission, "45000", "Hoan not phan con lai");
        assertThat((BigDecimal) commissionRow(commission).get("commission_amount")).isEqualByComparingTo("0");
        assertThat(commissionRow(commission).get("status")).isEqualTo("waived");
        assertThat(paymentStatus(paymentId)).isEqualTo("refunded");
        assertThat(detail.get("refunds")).hasSize(3);
        // Het khoan da thu: khong hoan them duoc.
        expectStatus(commission, body("1", "Hoan them khong duoc"), 409);
    }

    @Test
    @DisplayName("Booking co voucher: hoa hong, phan Courtly boi va khoan Courtly no chu san cung giam theo ty le phan con lai cua khoan da thu")
    void voucherBooking() throws Exception {
        BookingCommission commission = commission(owner, venue, "due", "0", 3, null);
        jdbc.update("UPDATE booking_commissions SET voucher_discount = 72000, courtly_owed = 54000 WHERE id = ?", commission.getId());
        payment(commission, "confirmed", "108000");
        refund(commission, "54000", "Hoan mot nua so tien nguoi choi da tra");
        Map<String, Object> row = commissionRow(commission);
        assertThat((BigDecimal) row.get("commission_amount")).isEqualByComparingTo("0");
        assertThat((BigDecimal) row.get("voucher_discount")).isEqualByComparingTo("36000");
        assertThat((BigDecimal) row.get("courtly_owed")).isEqualByComparingTo("27000");

        BookingCommission other = commission(owner, venue, "due", "0", 3, null);
        jdbc.update("UPDATE booking_commissions SET voucher_discount = 72000, courtly_owed = 54000 WHERE id = ?", other.getId());
        payment(other, "confirmed", "108000");
        refund(other, "108000", "Hoan toan bo");
        Map<String, Object> full = commissionRow(other);
        assertThat((BigDecimal) full.get("voucher_discount")).isEqualByComparingTo("0");
        assertThat((BigDecimal) full.get("courtly_owed")).isEqualByComparingTo("0");
        assertThat(full.get("status")).isEqualTo("waived");
    }

    @Test
    @DisplayName("Hoa hong thuoc ky: so phai thu cua ky giam theo; ky da thu vuot so phai thu moi thi ky paid va phan thua thanh so du")
    void insideSettlement() throws Exception {
        CommissionSettlement partial = settlement(owner, "30000", "0", "open");
        BookingCommission a = commission(owner, venue, "due", "18000", 3, partial);
        commission(owner, venue, "due", "12000", 3, partial);
        payment(a, "confirmed", null);
        refund(a, "90000", "Hoan mot nua");
        assertThat((BigDecimal) settlementRow(partial).get("amount_due")).isEqualByComparingTo("21000");
        assertThat((BigDecimal) settlementRow(partial).get("adjustment_total")).isEqualByComparingTo("-9000");
        assertThat(credit()).isEqualByComparingTo("0");

        // Ky khac: da thu 25.000 nhung hoan toan bo hoa hong 18.000 -> con phai thu 12.000 < 25.000: ky paid, thua 13.000 thanh so du.
        CommissionSettlement paid = settlement(owner, "30000", "25000", "partial");
        BookingCommission b = commission(owner, venue, "due", "18000", 3, paid);
        BookingCommission c = commission(owner, venue, "due", "12000", 5, paid);
        payment(b, "confirmed", null);
        refund(b, "180000", "Hoan toan bo");
        Map<String, Object> row = settlementRow(paid);
        assertThat(row.get("status")).isEqualTo("paid");
        assertThat((BigDecimal) row.get("amount_due")).isEqualByComparingTo("25000");
        assertThat(statusOf(c)).isEqualTo("settled");
        assertThat(credit()).isEqualByComparingTo("13000");
    }

    @Test
    @DisplayName("Hoa hong da doi soat roi moi hoan: giu trang thai settled, phan thua thanh so du va so du dong luon hoa hong cu nhat chua thuoc ky")
    void settledCommissionBecomesCredit() throws Exception {
        BookingCommission settled = commission(owner, venue, "settled", "18000", -5, null);
        payment(settled, "confirmed", null);
        BookingCommission loose = commission(owner, venue, "due", "8000", 1, null);
        refund(settled, "90000", "Hoan mot nua sau khi da thanh toan hoa hong");
        assertThat((BigDecimal) commissionRow(settled).get("commission_amount")).isEqualByComparingTo("9000");
        assertThat(commissionRow(settled).get("status")).isEqualTo("settled");
        // 9.000 du dong hoa hong 8.000, con 1.000.
        assertThat(statusOf(loose)).isEqualTo("settled");
        assertThat(credit()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Giam hoa hong chua thuoc ky: so du hien co co the dong luon hoa hong cu nhat")
    void looseCommissionUsesExistingCredit() throws Exception {
        BookingCommission a = commission(owner, venue, "due", "18000", 3, null);
        BookingCommission b = commission(owner, venue, "due", "20000", 5, null);
        payment(a, "confirmed", null);
        jdbc.update("UPDATE court_owner_profiles SET commission_credit = 10000 WHERE user_id = ?", owner.getId());
        entityManager.flush();
        entityManager.clear();
        refund(a, "90000", "Hoan mot nua");
        // a con 9.000, so du 10.000 dong a (cu nhat), du 1.000.
        assertThat(statusOf(a)).isEqualTo("settled");
        assertThat(statusOf(b)).isEqualTo("due");
        assertThat(credit()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Tinh lai han che cong no sau khi hoan tien lam giam hoa hong qua han")
    void recomputesRestriction() throws Exception {
        CommissionSettlement settlement = settlement(owner, "18000", "0", "overdue");
        BookingCommission a = commission(owner, venue, "due", "18000", -3, settlement);
        payment(a, "confirmed", null);
        jdbc.update("UPDATE debt_restriction_levels SET enabled = true, min_overdue_amount = 10000, min_overdue_days = NULL WHERE level = 1");
        jdbc.update("UPDATE court_owner_profiles SET restriction_level = 1 WHERE user_id = ?", owner.getId());
        entityManager.flush();
        entityManager.clear();
        refund(a, "180000", "Hoan toan bo");
        assertThat(jdbc.queryForObject("SELECT restriction_level FROM court_owner_profiles WHERE user_id = ?", Integer.class, owner.getId())).isZero();
    }

    @Test
    @DisplayName("Khong ghi nhan duoc: chua thu tien, hoan vuot so da thu, hoa hong waived / suspended; khong de lai khoan hoan nao")
    void refusals() throws Exception {
        BookingCommission unpaid = commission(owner, venue, "due", "18000", 3, null);
        payment(unpaid, "payer_reported", null);
        expectStatus(unpaid, body("1000", "Chua thu tien nen khong hoan"), 409);
        BookingCommission noPayment = commission(owner, venue, "due", "18000", 3, null);
        expectStatus(noPayment, body("1000", "Khong co thanh toan nao"), 409);

        BookingCommission over = commission(owner, venue, "due", "18000", 3, null);
        payment(over, "confirmed", null);
        expectStatus(over, body("180001", "Hoan vuot so da thu"), 409);
        refund(over, "100000", "Hoan mot phan truoc");
        expectStatus(over, body("80001", "Vuot phan con lai"), 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refunds WHERE booking_id = ?", Long.class, over.getBooking().getId())).isEqualTo(1);

        for (String state : new String[] {"waived", "suspended"}) {
            BookingCommission blocked = commission(owner, venue, state, "18000", 3, null);
            payment(blocked, "confirmed", null);
            expectStatus(blocked, body("1000", "Hoa hong khong dieu chinh duoc"), 409);
            assertThat(commissionRow(blocked).get("commission_amount")).isEqualTo(new BigDecimal("18000.00"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refunds WHERE booking_id = ?", Long.class, unpaid.getBooking().getId())).isZero();
    }

    @Test
    @DisplayName("Kiem tra du lieu: thieu so tien / ly do, so khong hop le, ly do 5-500 ky tu; id 404 / sai dinh dang 400")
    void validation() throws Exception {
        BookingCommission a = commission(owner, venue, "due", "18000", 3, null);
        payment(a, "confirmed", null);
        String[] bad = {"{}", body("1000", null), body(null, "Ly do hop le"), body("0", "Ly do hop le"), body("-5", "Ly do hop le"),
                body("1000.5", "Ly do hop le"), body("99999999999", "Ly do hop le"), body("1000", ""), body("1000", "    "),
                body("1000", "ngan"), body("1000", "x".repeat(501)), "{\"amount\":\"abc\",\"reason\":\"Ly do hop le\"}"};
        for (String payload : bad) {
            expectStatus(a, payload, 400);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refunds WHERE booking_id = ?", Long.class, a.getBooking().getId())).isZero();
        mockMvc.perform(post(PATH + UUID.randomUUID() + "/refunds").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(body("1000", "Khong ton tai"))).andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "khong-phai-uuid/refunds").header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON).content(body("1000", "Sai dinh dang"))).andExpect(status().isBadRequest());
        refund(a, "1000", "y".repeat(500));
    }

    @Test
    @DisplayName("Can finance.commission.manage; chu san va nguoi choi 403; chua dang nhap 401; khong ghi gi khi bi chan")
    void access() throws Exception {
        BookingCommission a = commission(owner, venue, "due", "18000", 3, null);
        payment(a, "confirmed", null);
        String path = PATH + a.getId() + "/refunds";
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
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refunds WHERE booking_id = ?", Long.class, a.getBooking().getId())).isZero();
        assertThat(hasSize(0)).isNotNull();
    }
}
