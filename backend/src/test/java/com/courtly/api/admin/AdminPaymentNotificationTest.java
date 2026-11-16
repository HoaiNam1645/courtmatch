package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.CommissionSettlement;
import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationPreference;
import com.courtly.domain.notification.NotificationPreferenceRepository;
import com.courtly.domain.notification.NotificationRepository;
import com.courtly.service.owner.OwnerRestrictionService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

/** 2.3.55 - Thong bao cho chu san khi Courtly xu ly cong no hoa hong va khi han che cong no doi muc. */
class AdminPaymentNotificationTest extends AdminFinanceTestBase {

    private static final String PATH = "/api/v1/admin/finance/commission-payments";

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationPreferenceRepository preferenceRepository;
    @Autowired private OwnerRestrictionService restrictionService;

    private List<Notification> ownerNotifications(String type) {
        entityManager.flush();
        return notificationRepository.findForUser(owner.getId(), type, false, PageRequest.of(0, 50)).getContent();
    }

    private void act(UUID reportId, String action, String body, int expected) throws Exception {
        var request = post(PATH + "/" + reportId + "/" + action).header("Authorization", bearer(adminToken));
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        mockMvc.perform(request).andExpect(status().is(expected));
    }

    @Test
    @DisplayName("Admin xac nhan thanh toan gan ky: chu san nhan thong bao co so tien, ma tham chieu, khoang ky va route toi lich su thanh toan")
    void confirmedForSettlement() throws Exception {
        CommissionSettlement settlement = settlement(owner, "30000", "0", "pending_review");
        commission(owner, venue, "due", "30000", 3, settlement);
        UUID reportId = report(owner, settlement, "30000");
        act(reportId, "confirm", null, 200);

        List<Notification> sent = ownerNotifications("payment_status");
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getTitle()).isEqualTo("Courtly đã xác nhận thanh toán công nợ");
        String period = com.courtly.service.notification.PaymentNotifier.period(settlement.getPeriodFrom(), settlement.getPeriodTo());
        assertThat(sent.getFirst().getContent()).contains("30.000đ").contains("cho kỳ " + period).contains("đã được xác nhận");
        assertThat(sent.getFirst().getDataJson()).containsEntry("route", "/owner/finance?tab=payment-history")
                .containsEntry("reportId", reportId.toString());
        assertThat(notificationRepository.findForUser(admin.getId(), null, false, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    @DisplayName("Thanh toan chung va khoan Courtly no duoc bu tru deu duoc noi ro trong thong bao")
    void confirmedGeneralWithOffset() throws Exception {
        commission(owner, venue, "due", "30000", 3, null);
        BookingCommission owed = commission(owner, venue, "settled", "0", 3, null);
        jdbc.update("UPDATE booking_commissions SET courtly_owed = 5000, voucher_discount = 5000 WHERE id = ?", owed.getId());
        act(report(owner, null, "25000"), "confirm", null, 200);

        Notification notification = ownerNotifications("payment_status").getFirst();
        assertThat(notification.getContent()).contains("vào công nợ chung").contains("25.000đ").contains("bù trừ thêm 5.000đ");
    }

    @Test
    @DisplayName("Admin tu choi: chu san nhan thong bao kem ly do va huong dan gui lai; xac nhan/tu choi that bai khong tao thong bao")
    void rejectedAndFailures() throws Exception {
        UUID reportId = report(owner, null, "10000");
        act(reportId, "reject", "{\"reason\":\"Bang chung mo khong doc duoc\"}", 200);
        List<Notification> sent = ownerNotifications("payment_status");
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getTitle()).isEqualTo("Courtly từ chối báo cáo thanh toán công nợ");
        assertThat(sent.getFirst().getContent()).contains("Bang chung mo khong doc duoc").contains("gửi lại").contains("10.000đ");

        // Da xu ly roi, ly do ngan, bao cao khong ton tai: khong co thong bao moi.
        act(reportId, "reject", "{\"reason\":\"Lan hai khong duoc\"}", 409);
        act(reportId, "confirm", null, 409);
        act(report(owner, null, "5000"), "reject", "{\"reason\":\"ngan\"}", 400);
        act(UUID.randomUUID(), "confirm", null, 404);
        assertThat(ownerNotifications("payment_status")).hasSize(1);
    }

    @Test
    @DisplayName("Xac nhan vuot cong no (409) khong gui thong bao; admin ghi nhan thanh toan cung thong bao cho chu san")
    void recordNotifiesOwnerAndOversizeDoesNot() throws Exception {
        commission(owner, venue, "due", "10000", 3, null);
        act(report(owner, null, "99999"), "confirm", null, 409);
        assertThat(ownerNotifications("payment_status")).isEmpty();

        String body = "{\"ownerId\":\"" + owner.getId() + "\",\"amount\":10000,\"paidOn\":\"" + LocalDate.now(VN)
                + "\",\"referenceCode\":\"TIEN-MAT-THONG-BAO\"}";
        mockMvc.perform(post(PATH).header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        List<Notification> sent = ownerNotifications("payment_status");
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getContent()).contains("TIEN-MAT-THONG-BAO").contains("10.000đ");
    }

    @Test
    @DisplayName("Chu san tat loai payment_status: van xu ly duoc nhung khong co thong bao")
    void respectsOwnerPreference() throws Exception {
        NotificationPreference preference = new NotificationPreference(owner, "payment_status");
        preference.setInAppEnabled(false);
        preferenceRepository.saveAndFlush(preference);
        commission(owner, venue, "due", "10000", 3, null);
        act(report(owner, null, "10000"), "confirm", null, 200);
        assertThat(ownerNotifications("payment_status")).isEmpty();
    }

    @Test
    @DisplayName("Han che cong no doi muc: bi han che muc 1, tang muc 2, gion xuong, go han che deu bao chu san; khong doi muc thi khong bao; loai bat buoc luon den")
    void restrictionChangesNotifyOwner() throws Exception {
        jdbc.update("UPDATE debt_restriction_levels SET enabled = true, min_overdue_amount = 10000, min_overdue_days = NULL WHERE level = 1");
        jdbc.update("UPDATE debt_restriction_levels SET enabled = true, min_overdue_amount = 50000, min_overdue_days = NULL WHERE level = 2");
        // Chu san tat moi kenh cua loai nay: loai bat buoc van co thong bao trong ung dung.
        NotificationPreference preference = new NotificationPreference(owner, "debt_restriction");
        preference.setInAppEnabled(false);
        preference.setEmailEnabled(false);
        preferenceRepository.saveAndFlush(preference);
        BookingCommission small = commission(owner, venue, "due", "20000", -3, null);
        // Tao truoc moi booking: sau khi bi han che muc 1 chu san khong nhan duoc booking moi.
        BookingCommission large = commission(owner, venue, "due", "40000", 3, null);
        entityManager.flush();
        entityManager.clear();

        assertThat(restrictionService.recompute(owner.getId())).isTrue();
        List<Notification> sent = ownerNotifications("debt_restriction");
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getTitle()).isEqualTo("Bị hạn chế nhận booking mới do công nợ");
        assertThat(sent.getFirst().getContent()).contains("20.000đ").contains("không nhận được booking mới");
        assertThat(sent.getFirst().getDataJson()).containsEntry("route", "/owner/finance?tab=debt").containsEntry("level", 1);

        // Khong doi muc thi khong thong bao them.
        assertThat(restrictionService.recompute(owner.getId())).isFalse();
        assertThat(ownerNotifications("debt_restriction")).hasSize(1);

        // Hoa hong lon qua han: tong qua han 60.000 dat nguong muc 2.
        jdbc.update("UPDATE booking_commissions SET due_at = now() - interval '3 days' WHERE id = ?", large.getId());
        entityManager.clear();
        assertThat(restrictionService.recompute(owner.getId())).isTrue();
        assertThat(ownerNotifications("debt_restriction").getFirst().getTitle()).isEqualTo("Sân bị tạm ẩn do công nợ quá hạn");
        assertThat(ownerNotifications("debt_restriction").getFirst().getContent()).contains("60.000đ").contains("tạm thời bị ẩn");

        jdbc.update("UPDATE booking_commissions SET status = 'settled' WHERE id = ?", large.getId());
        entityManager.clear();
        assertThat(restrictionService.recompute(owner.getId())).isTrue();
        assertThat(ownerNotifications("debt_restriction").getFirst().getTitle()).isEqualTo("Hạn chế công nợ giảm xuống mức 1");

        jdbc.update("UPDATE booking_commissions SET status = 'settled' WHERE id = ?", small.getId());
        entityManager.clear();
        assertThat(restrictionService.recompute(owner.getId())).isTrue();
        Notification lifted = ownerNotifications("debt_restriction").getFirst();
        assertThat(lifted.getTitle()).isEqualTo("Đã gỡ hạn chế do công nợ");
        assertThat(lifted.getContent()).contains("hoạt động và nhận booking bình thường");
        assertThat(ownerNotifications("debt_restriction")).hasSize(4);
    }
}
