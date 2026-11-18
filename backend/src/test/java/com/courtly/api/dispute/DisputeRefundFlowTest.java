package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.service.dispute.DisputeRefundService;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** 2.3.108 theo doi hoan tien truc tiep; 2.3.109 dieu chinh hoa hong khi hoan tien duoc xac nhan. */
class DisputeRefundFlowTest extends DisputeFinanceTestBase {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final String NOTE = "Camera cho thay san dong cua dung nhu nguoi choi bao";

    @Autowired private DisputeRefundService refundService;

    private String decide(UUID[] ids, String kind) throws Exception {
        adminPost("/api/v1/admin/booking-disputes/" + ids[0] + "/" + kind, "{\"note\":\"" + NOTE + "\"}", 200);
        entityManager.flush();
        return jdbc.queryForObject("SELECT id::text FROM refunds WHERE dispute_id = ?", String.class, ids[0]);
    }

    private JsonNode ownerReport(String token, String refundId, byte[] bytes, int expected) throws Exception {
        var file = new MockMultipartFile("file", "ck.png", "application/octet-stream", bytes);
        return objectMapper.readTree(mockMvc.perform(multipart("/api/v1/owner/refunds/" + refundId + "/report-paid").file(file)
                .param("note", "Da chuyen khoan luc 10h").header("Authorization", bearer(token))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode playerPost(String token, String path, String json, int expected) throws Exception {
        var request = post(path).header("Authorization", bearer(token));
        if (json != null) request = request.contentType(MediaType.APPLICATION_JSON).content(json);
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private String refundStatus(String refundId) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM refunds WHERE id = ?", String.class, UUID.fromString(refundId));
    }

    @Test
    @DisplayName("Hoan mot phan: chu san bao da hoan, nguoi choi xac nhan; hoa hong giam theo phan con lai, payment partially_refunded, thong bao day du")
    void partialRefundFullFlow() throws Exception {
        UUID[] ids = refundableDispute(1, 125000);
        String refundId = decide(ids, "refund-full");
        assertThat(commissionStatus(ids[1])).isEqualTo("suspended");
        JsonNode view = getJson("/api/v1/booking-disputes/" + ids[0] + "/refund", ownerToken).get("refund");
        assertThat(view.get("status").asText()).isEqualTo("awaiting_owner_refund");
        assertThat(view.get("canReport").asBoolean()).isTrue();
        assertThat(getJson("/api/v1/booking-disputes/" + ids[0] + "/refund", playerToken).get("refund").get("canReport").asBoolean()).isFalse();

        ownerReport(ownerToken, refundId, PNG, 200);
        assertThat(refundStatus(refundId)).isEqualTo("owner_reported_refunded");
        JsonNode playerView = getJson("/api/v1/booking-disputes/" + ids[0] + "/refund", playerToken).get("refund");
        assertThat(playerView.get("canConfirm").asBoolean()).isTrue();
        assertThat(playerView.get("proofUrl").asText()).endsWith("/proof");
        assertThat(playerView.get("autoConfirmAt").asText()).isNotBlank();
        assertThat(commissionStatus(ids[1])).isEqualTo("suspended");
        mockMvc.perform(get(playerView.get("proofUrl").asText()).header("Authorization", bearer(playerToken))).andExpect(status().isOk());
        mockMvc.perform(get(playerView.get("proofUrl").asText()).header("Authorization", bearer(loginAs(persistUser("stranger", com.courtly.domain.account.Role.PLAYER))))).andExpect(status().isNotFound());

        playerPost(playerToken, "/api/v1/refunds/" + refundId + "/confirm", null, 200);
        assertThat(refundStatus(refundId)).isEqualTo("confirmed");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE booking_id = ?", String.class, ids[1])).isEqualTo("partially_refunded");
        // 20.000 x (180.000 - 125.000) / 180.000 = 6.111d, trang thai adjusted (khong con suspended).
        assertThat(commissionStatus(ids[1])).isEqualTo("adjusted");
        assertThat(commissionAmount(ids[1])).isEqualByComparingTo("6111");
        assertThat(jdbc.queryForObject("SELECT confirmed_by FROM refunds WHERE id = ?", UUID.class, UUID.fromString(refundId))).isEqualTo(player.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action IN ('refund.owner_report', 'refund.confirm', 'commission.refund_adjust') AND created_at >= now()", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE type = 'booking_refund_updated' AND created_at >= now()", Integer.class)).isGreaterThanOrEqualTo(4);
        playerPost(playerToken, "/api/v1/refunds/" + refundId + "/confirm", null, 409);
        assertThat(commissionAmount(ids[1])).isEqualByComparingTo("6111");
    }

    @Test
    @DisplayName("Hoan toan bo: hoa hong thanh waived, payment refunded; khieu nai dua khoan hoan ve cho chu san, chu san bao lai duoc")
    void complaintThenFullRefund() throws Exception {
        UUID[] ids = refundableDispute(1, 180000);
        String refundId = decide(ids, "refund-full");
        ownerReport(ownerToken, refundId, PNG, 200);
        playerPost(playerToken, "/api/v1/refunds/" + refundId + "/dispute", "{\"reason\":\"abc\"}", 400);
        JsonNode complained = playerPost(playerToken, "/api/v1/refunds/" + refundId + "/dispute", "{\"reason\":\"Toi chua thay tien ve tai khoan\"}", 200);
        assertThat(complained.get("status").asText()).isEqualTo("awaiting_owner_refund");
        assertThat(complained.get("playerComplaint").asText()).contains("chua thay tien");
        assertThat(commissionStatus(ids[1])).isEqualTo("suspended");
        playerPost(playerToken, "/api/v1/refunds/" + refundId + "/confirm", null, 409);

        ownerReport(ownerToken, refundId, PNG, 200);
        playerPost(playerToken, "/api/v1/refunds/" + refundId + "/confirm", null, 200);
        assertThat(commissionStatus(ids[1])).isEqualTo("waived");
        assertThat(commissionAmount(ids[1])).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE booking_id = ?", String.class, ids[1])).isEqualTo("refunded");
    }

    @Test
    @DisplayName("Chu san bao hoan: bat buoc anh hop le, dung chu san, dung trang thai; nguoi choi khong bao thay")
    void ownerReportGuards() throws Exception {
        UUID[] ids = refundableDispute(1, 90000);
        String refundId = decide(ids, "refund-half");
        ownerReport(ownerToken, refundId, new byte[0], 400);
        ownerReport(ownerToken, refundId, new byte[] {1, 2, 3}, 400);
        ownerReport(playerToken, refundId, PNG, 403);
        ownerReport(loginAs(persistOwner("other-owner-refund")), refundId, PNG, 404);
        playerPost(playerToken, "/api/v1/refunds/" + refundId + "/confirm", null, 409);
        playerPost(ownerToken, "/api/v1/refunds/" + refundId + "/confirm", null, 404);
        ownerReport(ownerToken, refundId, PNG, 200);
        ownerReport(ownerToken, refundId, PNG, 409);
        assertThat(refundStatus(refundId)).isEqualTo("owner_reported_refunded");
    }

    @Test
    @DisplayName("Admin xac nhan thay ke ca khi chu san chua gui chung tu; can ghi chu; khong xac nhan hai lan")
    void adminConfirms() throws Exception {
        UUID[] ids = refundableDispute(1, 100000);
        String refundId = decide(ids, "refund-full");
        adminPost("/api/v1/admin/refunds/" + refundId + "/confirm", "{}", 400);
        mockMvc.perform(post("/api/v1/admin/refunds/" + refundId + "/confirm").header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Da doi chieu\"}")).andExpect(status().isForbidden());
        JsonNode result = adminPost("/api/v1/admin/refunds/" + refundId + "/confirm", "{\"note\":\"Da doi chieu sao ke ngan hang\"}", 200);
        assertThat(result.get("status").asText()).isEqualTo("confirmed");
        assertThat(commissionStatus(ids[1])).isEqualTo("adjusted");
        assertThat(jdbc.queryForObject("SELECT reason FROM refunds WHERE id = ?", String.class, UUID.fromString(refundId))).contains("Xac nhan: Da doi chieu");
        adminPost("/api/v1/admin/refunds/" + refundId + "/confirm", "{\"note\":\"Da doi chieu sao ke ngan hang\"}", 409);
    }

    @Test
    @DisplayName("Tu xac nhan sau 7 ngay nguoi choi khong phan hoi: chi khoan da qua han, dieu chinh hoa hong dung mot lan, ghi la tu xac nhan")
    void autoConfirm() throws Exception {
        UUID[] late = refundableDispute(1, 90000);
        UUID[] fresh = refundableDispute(2, 90000);
        String lateRefund = decide(late, "refund-full");
        String freshRefund = decide(fresh, "refund-full");
        ownerReport(ownerToken, lateRefund, PNG, 200);
        ownerReport(ownerToken, freshRefund, PNG, 200);
        jdbc.update("UPDATE refunds SET owner_reported_at = now() - interval '8 days' WHERE id = ?", UUID.fromString(lateRefund));
        jdbc.update("UPDATE refunds SET owner_reported_at = now() - interval '2 days' WHERE id = ?", UUID.fromString(freshRefund));
        entityManager.clear();

        assertThat(refundService.autoConfirmDue(Instant.now())).isEqualTo(1);
        assertThat(refundStatus(lateRefund)).isEqualTo("confirmed");
        assertThat(refundStatus(freshRefund)).isEqualTo("owner_reported_refunded");
        assertThat(jdbc.queryForObject("SELECT auto_confirmed FROM refunds WHERE id = ?", Boolean.class, UUID.fromString(lateRefund))).isTrue();
        assertThat(commissionStatus(late[1])).isEqualTo("adjusted");
        BigDecimal adjusted = commissionAmount(late[1]);
        assertThat(refundService.autoConfirmDue(Instant.now())).isZero();
        assertThat(commissionAmount(late[1])).isEqualByComparingTo(adjusted);
        assertThat(commissionStatus(fresh[1])).isEqualTo("suspended");
    }
}
