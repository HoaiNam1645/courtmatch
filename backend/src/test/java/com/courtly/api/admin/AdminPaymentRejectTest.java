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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 2.3.48 - Tu choi hoac yeu cau bo sung bang chung thanh toan cong no. */
class AdminPaymentRejectTest extends AdminFinanceTestBase {

    private static final String PATH = "/api/v1/admin/finance/commission-payments";

    private JsonNode reject(UUID reportId, String reason) throws Exception {
        String body = reason == null ? "{}" : "{\"reason\":\"" + reason + "\"}";
        return objectMapper.readTree(mockMvc.perform(post(PATH + "/" + reportId + "/reject").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String reportStatus(UUID id) {
        return jdbc.queryForObject("SELECT status FROM commission_payment_reports WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("Tu choi kem ly do: bao cao rejected, ghi nguoi xu ly + ly do + thoi diem, cong no khong doi, ky ve open")
    void rejectKeepsDebtAndRestoresSettlement() throws Exception {
        CommissionSettlement settlement = settlement(owner, "30000", "0", "pending_review");
        BookingCommission a = commission(owner, venue, "due", "18000", 5, settlement);
        BookingCommission b = commission(owner, venue, "due", "12000", 5, settlement);
        UUID reportId = report(owner, settlement, "30000");

        JsonNode result = reject(reportId, "  Bang chung mo, khong thay giao dich  ");
        assertThat(result.get("status").asText()).isEqualTo("rejected");
        assertThat(result.get("reviewNote").asText()).isEqualTo("Bang chung mo, khong thay giao dich");
        assertThat(result.get("reviewedByName").asText()).isEqualTo(admin.getFullName());
        assertThat(result.get("reviewedAt").asText()).isNotBlank();
        assertThat(statusOf(a)).isEqualTo("due");
        assertThat(statusOf(b)).isEqualTo("due");
        assertThat(settlementRow(settlement).get("status")).isEqualTo("open");
        assertThat((BigDecimal) settlementRow(settlement).get("amount_paid")).isEqualByComparingTo("0");
        assertThat(credit(owner)).isEqualByComparingTo("0");

        entityManager.flush();
        List<String> actions = jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_id IN (?, ?)", String.class, reportId, settlement.getId());
        assertThat(actions).containsExactlyInAnyOrder("settlement.payment.reject", "settlement.payment.reject");
        assertThat(jdbc.queryForObject("SELECT new_data ->> 'reason' FROM audit_logs WHERE entity_id = ? AND entity_type = 'commission_payment_reports'",
                String.class, reportId)).isEqualTo("Bang chung mo, khong thay giao dich");
    }

    @Test
    @DisplayName("Ky ve partial neu da thu mmot phan, ve overdue neu co hoa hong qua han; con bao cao khac cho xac nhan thi giu pending_review")
    void settlementStatusAfterReject() throws Exception {
        CommissionSettlement partial = settlement(owner, "30000", "10000", "pending_review");
        commission(owner, venue, "due", "30000", 5, partial);
        reject(report(owner, partial, "5000"), "Sai so tien chuyen khoan");
        assertThat(settlementRow(partial).get("status")).isEqualTo("partial");

        CommissionSettlement overdue = settlement(owner, "18000", "0", "pending_review");
        commission(owner, venue, "due", "18000", -3, overdue);
        reject(report(owner, overdue, "18000"), "Chua nhan duoc tien");
        assertThat(settlementRow(overdue).get("status")).isEqualTo("overdue");

        CommissionSettlement twoReports = settlement(owner, "20000", "0", "pending_review");
        commission(owner, venue, "due", "20000", 5, twoReports);
        UUID first = report(owner, twoReports, "5000");
        UUID second = report(owner, twoReports, "6000");
        reject(first, "Ma tham chieu sai");
        assertThat(settlementRow(twoReports).get("status")).isEqualTo("pending_review");
        reject(second, "Ma tham chieu sai");
        assertThat(settlementRow(twoReports).get("status")).isEqualTo("open");
    }

    @Test
    @DisplayName("Bao cao khong gan ky bi tu choi: khong dung vao ky nao; chu san gui lai bang ma tham chieu cu duoc (bao cao bi tu choi khong giu ma)")
    void rejectWithoutSettlement() throws Exception {
        commission(owner, venue, "due", "10000", 1, null);
        UUID reportId = report(owner, null, "10000");
        JsonNode result = reject(reportId, "Thieu bang chung thanh toan");
        assertThat(result.get("settlement")).isNull();
        assertThat(credit(owner)).isEqualByComparingTo("0");
        String reference = jdbc.queryForObject("SELECT reference_code FROM commission_payment_reports WHERE id = ?", String.class, reportId);
        // Rang buoc duy nhat chi ap dung cho bao cao chua bi tu choi nen gui lai cung ma van hop le.
        jdbc.update("INSERT INTO commission_payment_reports (id, owner_id, amount, paid_on, reference_code, status) "
                + "VALUES (gen_random_uuid(), ?, 10000, current_date, ?, 'pending_review')", owner.getId(), reference);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commission_payment_reports WHERE owner_id = ? AND reference_code = ?",
                Long.class, owner.getId(), reference)).isEqualTo(2);
    }

    @Test
    @DisplayName("Ly do bat buoc 5-500 ky tu, bao cao da xu ly 409, khong ton tai 404, id sai dinh dang 400; khong ghi gi khi that bai")
    void validation() throws Exception {
        UUID reportId = report(owner, null, "10000");
        for (String body : new String[] {"", "{}", "{\"reason\":\"\"}", "{\"reason\":\"    \"}", "{\"reason\":\"ngan\"}",
                "{\"reason\":\"" + "x".repeat(501) + "\"}"}) {
            mockMvc.perform(post(PATH + "/" + reportId + "/reject").header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(reportStatus(reportId)).isEqualTo("pending_review");
        assertThat(reject(reportId, "x".repeat(500)).get("status").asText()).isEqualTo("rejected");

        // Da xu ly: khong tu choi lai, khong xac nhan duoc.
        mockMvc.perform(post(PATH + "/" + reportId + "/reject").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Ly do khac\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMISSION_PAYMENT_INVALID"));
        mockMvc.perform(post(PATH + "/" + reportId + "/confirm").header("Authorization", bearer(adminToken)))
                .andExpect(status().isConflict());
        UUID confirmed = report(owner, null, "1");
        jdbc.update("UPDATE commission_payment_reports SET status = 'confirmed' WHERE id = ?", confirmed);
        mockMvc.perform(post(PATH + "/" + confirmed + "/reject").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khong duoc tu choi\"}"))
                .andExpect(status().isConflict());
        assertThat(reportStatus(confirmed)).isEqualTo("confirmed");

        mockMvc.perform(post(PATH + "/" + UUID.randomUUID() + "/reject").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Khong ton tai\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "/khong-phai-uuid/reject").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sai dinh dang\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Can finance.commission.manage; chu san va nguoi choi 403; chua dang nhap 401; bao cao van cho xu ly sau khi bi chan")
    void access() throws Exception {
        UUID reportId = report(owner, null, "10000");
        String path = PATH + "/" + reportId + "/reject";
        String body = "{\"reason\":\"Khong du quyen\"}";
        mockMvc.perform(post(path).header("Authorization", bearer(loginAs(owner))).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path).header("Authorization", bearer(player)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());

        Role adminRole = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        adminRole.getRolePermissions().removeIf(item -> "finance.commission.manage".equals(item.getPermission().getCode()));
        roleRepository.saveAndFlush(adminRole);
        mockMvc.perform(post(path).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        assertThat(reportStatus(reportId)).isEqualTo("pending_review");
    }
}
