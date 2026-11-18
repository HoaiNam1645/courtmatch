package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.notification.NotificationType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

/** 2.3.111 - moi buoc cua luong bao cao thong bao dung nguoi, dung loai, co lien ket toi man hinh chi tiet cua nguoi nhan. */
class DisputeNotificationMatrixTest extends DisputeFinanceTestBase {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final String NOTE = "Camera cho thay san dong cua dung nhu nguoi choi bao";

    /** Cac thong bao (loai) cua mot nguoi ve bao cao nay kem route, moi nhat truoc. */
    private List<Map<String, Object>> sent(UUID user, String disputeId) {
        entityManager.flush();
        return jdbc.queryForList("SELECT type, data_json ->> 'route' AS route FROM notifications WHERE user_id = ? AND data_json ->> 'disputeId' = ? ORDER BY created_at DESC, id",
                user, disputeId);
    }

    private void expect(UUID user, String disputeId, String type, String route) {
        List<Map<String, Object>> rows = sent(user, disputeId);
        assertThat(rows).as("%s -> %s", type, route).anySatisfy(row -> {
            assertThat(row.get("type")).isEqualTo(type);
            assertThat(row.get("route")).isEqualTo(route);
        });
    }

    private void expectNone(UUID user, String disputeId, String type) {
        assertThat(sent(user, disputeId)).noneMatch(row -> type.equals(row.get("type")));
    }

    @Test
    @DisplayName("Luong day du: tao, bo sung, giai trinh, quyet dinh hoan, bao da hoan, khieu nai, xac nhan - dung nguoi nhan, loai va lien ket")
    void fullFlowMatrix() throws Exception {
        UUID bookingId = completed(1);
        payment(bookingId, "confirmed");
        String id = dispute(playerToken, bookingId, 201, "extra_charge", "Chu san thu them tien thue vot ngoai thoa thuan",
                "\"refundRequested\":true,\"refundAmount\":100000").get("id").asText();
        String ownerRoute = "/owner/disputes/" + id;
        String playerRoute = "/bao-cao/" + id;
        String adminRoute = "/admin/disputes/" + id;

        expect(owner.getId(), id, "booking_dispute_opened", ownerRoute);
        expectNone(player.getId(), id, "booking_dispute_opened");

        mockMvc.perform(post("/api/v1/booking-disputes/" + id + "/supplements").header("Authorization", bearer(playerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Bo sung them bien lai thu tien\"}")).andExpect(status().isCreated());
        expect(owner.getId(), id, "booking_dispute_updated", ownerRoute);

        mockMvc.perform(post("/api/v1/owner/booking-disputes/" + id + "/response").header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Chung toi chi thu phi thue vot theo bang gia\"}")).andExpect(status().isOk());
        expect(player.getId(), id, "booking_dispute_responded", playerRoute);
        expectNone(owner.getId(), id, "booking_dispute_responded");

        adminPost("/api/v1/admin/booking-disputes/" + id + "/refund-full", "{\"note\":\"" + NOTE + "\"}", 200);
        expect(owner.getId(), id, "booking_dispute_resolved", ownerRoute);
        expect(player.getId(), id, "booking_dispute_resolved", playerRoute);
        String refundId = jdbc.queryForObject("SELECT id::text FROM refunds WHERE dispute_id = ?", String.class, UUID.fromString(id));

        mockMvc.perform(multipart("/api/v1/owner/refunds/" + refundId + "/report-paid").file(new MockMultipartFile("file", "ck.png", "application/octet-stream", PNG))
                .header("Authorization", bearer(ownerToken))).andExpect(status().isOk());
        expect(player.getId(), id, "booking_refund_updated", playerRoute);
        expect(admin.getId(), id, "booking_refund_updated", adminRoute);

        mockMvc.perform(post("/api/v1/refunds/" + refundId + "/dispute").header("Authorization", bearer(playerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Toi chua thay tien ve tai khoan\"}")).andExpect(status().isOk());
        expect(owner.getId(), id, "booking_refund_updated", ownerRoute);
        assertThat(sent(admin.getId(), id).stream().filter(row -> "booking_refund_updated".equals(row.get("type"))).count()).isEqualTo(2);

        mockMvc.perform(multipart("/api/v1/owner/refunds/" + refundId + "/report-paid").file(new MockMultipartFile("file", "ck2.png", "application/octet-stream", PNG))
                .header("Authorization", bearer(ownerToken))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/refunds/" + refundId + "/confirm").header("Authorization", bearer(playerToken))).andExpect(status().isOk());
        assertThat(sent(owner.getId(), id).stream().filter(row -> "booking_refund_updated".equals(row.get("type"))).count()).isEqualTo(2);
        assertThat(sent(player.getId(), id).stream().filter(row -> "booking_refund_updated".equals(row.get("type"))).count()).isEqualTo(3);
    }

    @Test
    @DisplayName("Huy, bac bo (hoa hong quay lai), nhac gio thu 24, tu xu thua: dung nguoi nhan va lien ket tu man hinh chi tiet")
    void otherStepsMatrix() throws Exception {
        UUID b1 = completed(1);
        String cancelled = dispute(playerToken, b1, 201, "other", "Chu san thu them tien thue vot ngoai thoa thuan").get("id").asText();
        mockMvc.perform(post("/api/v1/booking-disputes/" + cancelled + "/cancel").header("Authorization", bearer(playerToken))).andExpect(status().isOk());
        expect(owner.getId(), cancelled, "booking_dispute_cancelled", "/owner/disputes/" + cancelled);
        expect(owner.getId(), cancelled, "commission_restored", "/owner/disputes/" + cancelled);

        UUID b2 = completed(2);
        String rejected = dispute(playerToken, b2, 201, "other", "Chu san thu them tien thue vot ngoai thoa thuan").get("id").asText();
        answer(rejected);
        adminPost("/api/v1/admin/booking-disputes/" + rejected + "/reject", "{\"note\":\"Khong du bang chung\"}", 200);
        expect(owner.getId(), rejected, "booking_dispute_resolved", "/owner/disputes/" + rejected);
        expect(player.getId(), rejected, "booking_dispute_resolved", "/bao-cao/" + rejected);
        expect(owner.getId(), rejected, "commission_restored", "/owner/disputes/" + rejected);
        expectNone(player.getId(), rejected, "commission_restored");
    }

    @Test
    @DisplayName("Thong bao lien quan tien la loai bat buoc: tat kenh trong ung dung van nhan; danh sach tuy chon danh dau mandatory")
    void moneyNotificationsAreMandatory() throws Exception {
        assertThat(NotificationType.MANDATORY).contains(NotificationType.BOOKING_DISPUTE_RESOLVED, NotificationType.BOOKING_REFUND_UPDATED,
                NotificationType.COMMISSION_RESTORED, NotificationType.VENUE_SUSPENDED, NotificationType.DEBT_RESTRICTION);
        assertThat(NotificationType.MANDATORY).doesNotContain(NotificationType.BOOKING_DISPUTE_OPENED, NotificationType.BOOKING_DISPUTE_REMINDER);
        for (String type : new String[] {"booking_dispute_resolved", "commission_restored"}) {
            jdbc.update("INSERT INTO notification_preferences (user_id, type, in_app_enabled, email_enabled) VALUES (?, ?, false, false) ON CONFLICT (user_id, type) DO UPDATE SET in_app_enabled = false", owner.getId(), type);
        }
        entityManager.clear();
        UUID bookingId = completed(1);
        String id = dispute(playerToken, bookingId, 201, "other", "Chu san thu them tien thue vot ngoai thoa thuan").get("id").asText();
        answer(id);
        adminPost("/api/v1/admin/booking-disputes/" + id + "/reject", "{\"note\":\"Khong du bang chung\"}", 200);
        expect(owner.getId(), id, "booking_dispute_resolved", "/owner/disputes/" + id);
        expect(owner.getId(), id, "commission_restored", "/owner/disputes/" + id);
    }
}
