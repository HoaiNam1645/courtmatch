package com.courtly.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationPreference;
import com.courtly.domain.notification.NotificationPreferenceRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 2.3.55 - Thong bao trang thai thanh toan giua nguoi choi va chu san. */
class PaymentStatusNotificationTest extends NotificationFlowTestBase {

    @Autowired private NotificationPreferenceRepository preferenceRepository;

    private void ownerReject(String bookingId, String reason) throws Exception {
        mockMvcPost("/api/v1/owner/payments/" + paymentOf(bookingId).getId() + "/reject", ownerToken, "{\"reason\":\"" + reason + "\"}", 200);
    }

    @Test
    @DisplayName("Nguoi choi bao da thanh toan: chu san nhan thong bao payment_status co ten nguoi choi, so tien, ma booking, san, route va metadata; nguoi choi khong nhan")
    void playerReportedNotifiesOwner() throws Exception {
        String bookingId = newBooking();
        accept(bookingId);
        assertThat(notificationsOf(owner)).isEmpty();
        reportPaid(bookingId);

        List<Notification> sent = notificationsOf(owner);
        assertThat(sent).hasSize(1);
        Notification notification = sent.getFirst();
        assertThat(notification.getType()).isEqualTo("payment_status");
        assertThat(notification.getTitle()).isEqualTo("Người chơi báo đã thanh toán");
        assertThat(notification.getContent()).contains(player.getFullName()).contains("180.000đ").contains("San Thong Bao")
                .contains("Hãy đối chiếu và xác nhận");
        assertThat(notification.getDataJson()).containsEntry("route", "/owner/payments")
                .containsEntry("paymentId", paymentOf(bookingId).getId().toString()).containsEntry("bookingId", bookingId);
        assertThat(notificationsOf(player, "payment_status")).isEmpty();
    }

    @Test
    @DisplayName("Chu san tu choi: nguoi choi nhan thong bao kem ly do va route toi trang thanh toan; bao lai thi chu san nhan thong bao lan nua")
    void ownerRejectedNotifiesPlayerAndReReportNotifiesOwner() throws Exception {
        String bookingId = newBooking();
        accept(bookingId);
        reportPaid(bookingId);
        ownerReject(bookingId, "Chua thay tien trong tai khoan");

        List<Notification> sent = notificationsOf(player, "payment_status");
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getTitle()).isEqualTo("Chủ sân chưa nhận được tiền");
        assertThat(sent.getFirst().getContent()).contains("Chua thay tien trong tai khoan").contains("180.000đ").contains("báo lại");
        assertThat(sent.getFirst().getDataJson()).containsEntry("route", "/payments/" + paymentOf(bookingId).getId());
        assertThat(notificationsOf(owner, "payment_status")).hasSize(1);

        reportPaid(bookingId);
        assertThat(notificationsOf(owner, "payment_status")).hasSize(2);
        assertThat(notificationsOf(player, "payment_status")).hasSize(1);
    }

    @Test
    @DisplayName("Chu san xac nhan da thu tien chi sinh thong bao xac nhan dat san (2.3.53), khong them thong bao payment_status cho nguoi choi")
    void confirmationIsNotDuplicated() throws Exception {
        String bookingId = newBooking();
        accept(bookingId);
        reportPaid(bookingId);
        mockMvcPost("/api/v1/owner/payments/" + paymentOf(bookingId).getId() + "/confirm", ownerToken, null, 200);
        assertThat(notificationsOf(player, "payment_status")).isEmpty();
        assertThat(notificationsOf(player, "booking_confirmed")).hasSize(2);
    }

    @Test
    @DisplayName("Thao tac that bai khong tao thong bao: bao thanh toan hai lan, tu choi khi chua bao, chu san khac")
    void failedActionsSendNothing() throws Exception {
        String bookingId = newBooking();
        accept(bookingId);
        reportPaid(bookingId);
        mockMvcPost("/api/v1/payments/" + paymentOf(bookingId).getId() + "/report-paid", playerToken, "{\"method\":\"cash\"}", 409);
        assertThat(notificationsOf(owner)).hasSize(1);

        String other = newBooking();
        accept(other);
        mockMvcPost("/api/v1/owner/payments/" + paymentOf(other).getId() + "/reject", ownerToken, "{\"reason\":\"Chua bao ma tu choi\"}", 409);
        String strangerToken = loginAs(persistOwner("flow-stranger-pay"));
        mockMvcPost("/api/v1/owner/payments/" + paymentOf(bookingId).getId() + "/reject", strangerToken, "{\"reason\":\"Khong phai san cua toi\"}", 404);
        assertThat(notificationsOf(player, "payment_status")).isEmpty();
        assertThat(notificationsOf(owner)).hasSize(1);
    }

    @Test
    @DisplayName("Tuy chon nguoi nhan duoc ton trong: chu san tat payment_status thi khong nhan nhung nguoi choi van bao duoc")
    void respectsPreferences() throws Exception {
        NotificationPreference preference = new NotificationPreference(owner, "payment_status");
        preference.setInAppEnabled(false);
        preferenceRepository.saveAndFlush(preference);
        String bookingId = newBooking();
        accept(bookingId);
        reportPaid(bookingId);
        assertThat(notificationsOf(owner)).isEmpty();
    }
}
