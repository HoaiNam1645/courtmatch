package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.CommissionSettlement;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 2.3.49 - Ghi nhan giao dich doi soat hoa hong do quan tri vien nhap. */
class AdminPaymentRecordTest extends AdminFinanceTestBase {

    private static final String PATH = "/api/v1/admin/finance/commission-payments";

    private String body(UUID ownerId, UUID settlementId, String amount, String paidOn, String reference, String proof, String note) {
        StringBuilder json = new StringBuilder("{");
        json.append("\"ownerId\":\"").append(ownerId).append("\"");
        if (settlementId != null) {
            json.append(",\"settlementId\":\"").append(settlementId).append("\"");
        }
        if (amount != null) {
            json.append(",\"amount\":").append(amount);
        }
        if (paidOn != null) {
            json.append(",\"paidOn\":\"").append(paidOn).append("\"");
        }
        if (reference != null) {
            json.append(",\"referenceCode\":\"").append(reference).append("\"");
        }
        if (proof != null) {
            json.append(",\"proofUrl\":\"").append(proof).append("\"");
        }
        if (note != null) {
            json.append(",\"note\":\"").append(note).append("\"");
        }
        return json.append("}").toString();
    }

    private String today() {
        return LocalDate.now(VN).toString();
    }

    private JsonNode record(String body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post(PATH).header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void expectStatus(String body, int expected) throws Exception {
        mockMvc.perform(post(PATH).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected));
    }

    private long reportCount() {
        return jdbc.queryForObject("SELECT count(*) FROM commission_payment_reports WHERE owner_id = ?", Long.class, owner.getId());
    }

    @Test
    @DisplayName("Ghi nhan du so tien ky: tao bao cao da xac nhan do admin nhap, ky paid, hoa hong settled, ky luu ma tham chieu va bang chung")
    void recordForSettlement() throws Exception {
        CommissionSettlement settlement = settlement(owner, "30000", "0", "open");
        BookingCommission a = commission(owner, venue, "due", "18000", 1, settlement);
        BookingCommission b = commission(owner, venue, "due", "12000", 2, settlement);

        JsonNode result = record(body(owner.getId(), settlement.getId(), "30000", today(), "  TIEN-MAT-001  ",
                "https://example.com/bien-nhan.png", "Thu tien mat tai van phong"));
        assertThat(result.get("status").asText()).isEqualTo("confirmed");
        assertThat(result.get("recordedByAdmin").asBoolean()).isTrue();
        assertThat(result.get("referenceCode").asText()).isEqualTo("TIEN-MAT-001");
        assertThat(result.get("note").asText()).isEqualTo("Thu tien mat tai van phong");
        assertThat(result.get("proofUrl").asText()).isEqualTo("https://example.com/bien-nhan.png");
        assertThat(result.get("reviewedByName").asText()).isEqualTo(admin.getFullName());
        assertThat(result.get("settlement").get("id").asText()).isEqualTo(settlement.getId().toString());

        assertThat(statusOf(a)).isEqualTo("settled");
        assertThat(statusOf(b)).isEqualTo("settled");
        Map<String, Object> row = jdbc.queryForMap("SELECT status, amount_paid, payment_reference, proof_url, paid_at FROM commission_settlements WHERE id = ?",
                settlement.getId());
        assertThat(row.get("status")).isEqualTo("paid");
        assertThat((BigDecimal) row.get("amount_paid")).isEqualByComparingTo("30000");
        assertThat(row.get("payment_reference")).isEqualTo("TIEN-MAT-001");
        assertThat(row.get("proof_url")).isEqualTo("https://example.com/bien-nhan.png");
        assertThat(row.get("paid_at")).isNotNull();

        entityManager.flush();
        UUID reportId = UUID.fromString(result.get("id").asText());
        List<String> actions = jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_id = ? ORDER BY action", String.class, reportId);
        assertThat(actions).containsExactlyInAnyOrder("settlement.payment.confirm", "settlement.payment.record");
    }

    @Test
    @DisplayName("Ghi nhan mot phan roi phan con lai; ghi nhan vao cong no chung dong hoa hong cu nhat va giu so du")
    void partialAndGeneral() throws Exception {
        CommissionSettlement settlement = settlement(owner, "100000", "0", "open");
        BookingCommission first = commission(owner, venue, "due", "60000", 1, settlement);
        BookingCommission second = commission(owner, venue, "due", "40000", 2, settlement);
        record(body(owner.getId(), settlement.getId(), "70000", today(), "REF-PHAN-1", null, null));
        assertThat(settlementRow(settlement).get("status")).isEqualTo("partial");
        assertThat(statusOf(first)).isEqualTo("settled");
        assertThat(statusOf(second)).isEqualTo("due");
        record(body(owner.getId(), settlement.getId(), "30000", today(), "REF-PHAN-2", null, null));
        assertThat(settlementRow(settlement).get("status")).isEqualTo("paid");
        assertThat(statusOf(second)).isEqualTo("settled");

        BookingCommission general = commission(owner, venue, "due", "10000", 1, null);
        BookingCommission later = commission(owner, venue, "due", "15000", 5, null);
        JsonNode result = record(body(owner.getId(), null, "12000", today(), "REF-CHUNG", null, null));
        assertThat(result.get("settlement")).isNull();
        assertThat(statusOf(general)).isEqualTo("settled");
        assertThat(statusOf(later)).isEqualTo("due");
        assertThat(jdbc.queryForObject("SELECT commission_credit FROM court_owner_profiles WHERE user_id = ?", BigDecimal.class, owner.getId()))
                .isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("Tinh lai han che cong no sau khi ghi nhan va bu tru khoan Courtly no")
    void offsetsAndLiftsRestriction() throws Exception {
        CommissionSettlement settlement = settlement(owner, "18000", "0", "overdue");
        commission(owner, venue, "due", "18000", -3, settlement);
        BookingCommission owed = commission(owner, venue, "settled", "0", 1, null);
        jdbc.update("UPDATE booking_commissions SET courtly_owed = 3000, voucher_discount = 3000 WHERE id = ?", owed.getId());
        jdbc.update("UPDATE debt_restriction_levels SET enabled = true, min_overdue_amount = 10000, min_overdue_days = NULL WHERE level = 1");
        jdbc.update("UPDATE court_owner_profiles SET restriction_level = 1 WHERE user_id = ?", owner.getId());
        entityManager.flush();
        entityManager.clear();

        JsonNode result = record(body(owner.getId(), settlement.getId(), "15000", today(), "REF-BU-TRU", null, null));
        assertThat(result.get("courtlyOffsetAmount").decimalValue()).isEqualByComparingTo("3000");
        assertThat(settlementRow(settlement).get("status")).isEqualTo("paid");
        assertThat(jdbc.queryForObject("SELECT restriction_level FROM court_owner_profiles WHERE user_id = ?", Integer.class, owner.getId())).isZero();
    }

    @Test
    @DisplayName("Ghi nhan khong hop le (vuot cong no, ky da paid/tranh chap) bi tu choi va khong de lai bao cao nao")
    void failedRecordLeavesNothing() throws Exception {
        CommissionSettlement settlement = settlement(owner, "20000", "5000", "partial");
        commission(owner, venue, "due", "20000", 1, settlement);
        long before = reportCount();
        expectStatus(body(owner.getId(), settlement.getId(), "15001", today(), "REF-VUOT", null, null), 409);
        assertThat(reportCount()).isEqualTo(before);

        CommissionSettlement paid = settlement(owner, "1000", "1000", "paid");
        expectStatus(body(owner.getId(), paid.getId(), "1000", today(), "REF-DA-TRA", null, null), 409);
        CommissionSettlement disputed = settlement(owner, "5000", "0", "disputed");
        commission(owner, venue, "due", "5000", 1, disputed);
        expectStatus(body(owner.getId(), disputed.getId(), "5000", today(), "REF-TRANH-CHAP", null, null), 409);
        // Khong co hoa hong nao chua thuoc ky: ghi nhan chung vuot 0 dong.
        expectStatus(body(owner.getId(), null, "1", today(), "REF-KHONG-NO", null, null), 409);
        assertThat(reportCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("Ma tham chieu trung (bao cao chua bi tu choi) 409, ma cua bao cao bi tu choi dung lai duoc; ma cua chu san khac khong anh huong")
    void duplicateReference() throws Exception {
        commission(owner, venue, "due", "50000", 1, null);
        commission(otherOwner, otherVenue, "due", "50000", 1, null);
        record(body(owner.getId(), null, "10000", today(), "REF-TRUNG", null, null));
        expectStatus(body(owner.getId(), null, "10000", today(), "ref-trung", null, null), 409);
        // Chu san khac dung cung ma van hop le.
        record(body(otherOwner.getId(), null, "10000", today(), "REF-TRUNG", null, null));
        UUID pending = report(owner, null, "5000");
        String pendingRef = jdbc.queryForObject("SELECT reference_code FROM commission_payment_reports WHERE id = ?", String.class, pending);
        expectStatus(body(owner.getId(), null, "5000", today(), pendingRef, null, null), 409);
        jdbc.update("UPDATE commission_payment_reports SET status = 'rejected' WHERE id = ?", pending);
        record(body(owner.getId(), null, "5000", today(), pendingRef, null, null));
    }

    @Test
    @DisplayName("Kiem tra du lieu: thieu truong, so tien, ngay tuong lai / qua xa, ma tham chieu, do dai bang chung va ghi chu")
    void validation() throws Exception {
        commission(owner, venue, "due", "50000", 1, null);
        UUID id = owner.getId();
        String[] bad = {
                "{}",
                "{\"ownerId\":\"" + id + "\"}",
                body(id, null, null, today(), "REF-OK", null, null),
                body(id, null, "0", today(), "REF-OK", null, null),
                body(id, null, "-5", today(), "REF-OK", null, null),
                body(id, null, "1000.5", today(), "REF-OK", null, null),
                body(id, null, "99999999999", today(), "REF-OK", null, null),
                body(id, null, "1000", LocalDate.now(VN).plusDays(1).toString(), "REF-OK", null, null),
                body(id, null, "1000", LocalDate.now(VN).minusDays(366).toString(), "REF-OK", null, null),
                body(id, null, "1000", null, "REF-OK", null, null),
                body(id, null, "1000", today(), null, null, null),
                body(id, null, "1000", today(), "  ", null, null),
                body(id, null, "1000", today(), "ab", null, null),
                body(id, null, "1000", today(), "x".repeat(101), null, null),
                body(id, null, "1000", today(), "REF-OK", "u".repeat(501), null),
                body(id, null, "1000", today(), "REF-OK", null, "n".repeat(501)),
                "{\"ownerId\":\"khong-phai-uuid\",\"amount\":1000,\"paidOn\":\"" + today() + "\",\"referenceCode\":\"REF-OK\"}",
                "{\"ownerId\":\"" + id + "\",\"amount\":1000,\"paidOn\":\"khong-phai-ngay\",\"referenceCode\":\"REF-OK\"}",
        };
        for (String payload : bad) {
            expectStatus(payload, 400);
        }
        assertThat(reportCount()).isZero();
    }

    @Test
    @DisplayName("Chu san khong ton tai / khong phai chu san 404; ky khong ton tai 404; ky cua chu san khac 400")
    void ownerAndSettlementLookups() throws Exception {
        expectStatus(body(UUID.randomUUID(), null, "1000", today(), "REF-LA", null, null), 404);
        UUID playerId = jdbc.queryForObject("SELECT id FROM users WHERE email LIKE 'player-confirm%'", UUID.class);
        expectStatus(body(playerId, null, "1000", today(), "REF-LA", null, null), 404);
        expectStatus(body(owner.getId(), UUID.randomUUID(), "1000", today(), "REF-LA", null, null), 404);
        CommissionSettlement others = settlement(otherOwner, "10000", "0", "open");
        commission(otherOwner, otherVenue, "due", "10000", 1, others);
        expectStatus(body(owner.getId(), others.getId(), "1000", today(), "REF-LA", null, null), 400);
        assertThat(settlementRow(others).get("status")).isEqualTo("open");
    }

    @Test
    @DisplayName("Can finance.commission.manage (xem thoi khong du); chu san va nguoi choi 403; chua dang nhap 401")
    void access() throws Exception {
        commission(owner, venue, "due", "50000", 1, null);
        String payload = body(owner.getId(), null, "1000", today(), "REF-QUYEN", null, null);
        for (String token : new String[] {loginAs(owner), player}) {
            mockMvc.perform(post(PATH).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isUnauthorized());
        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "finance.commission.manage".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(post(PATH).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(reportCount()).isZero();
    }
}
