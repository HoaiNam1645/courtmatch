package com.courtly.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationPreference;
import com.courtly.domain.notification.NotificationPreferenceRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 2.3.53 - Thong bao xac nhan dat san: chu san chap nhan / tu choi va xac nhan da thu tien. */
class BookingConfirmedNotificationTest extends NotificationFlowTestBase {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private NotificationPreferenceRepository preferenceRepository;

    @Test
    @DisplayName("Chu san chap nhan: nguoi choi nhan thong bao booking_confirmed co ma booking, san, gio, thoi han thanh toan, route va metadata; chu san khong nhan")
    void acceptedNotifiesPlayer() throws Exception {
        String bookingId = newBooking();
        assertThat(notificationsOf(player)).isEmpty();
        accept(bookingId);

        List<Notification> sent = notificationsOf(player);
        assertThat(sent).hasSize(1);
        Notification notification = sent.getFirst();
        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        assertThat(notification.getType()).isEqualTo("booking_confirmed");
        assertThat(notification.getTitle()).isEqualTo("Chủ sân đã chấp nhận đặt sân");
        assertThat(notification.getContent()).contains(booking.getBookingCode()).contains("San Thong Bao").contains("30 phút")
                .containsPattern("\\d{2}:\\d{2} \\d{2}/\\d{2}/\\d{4}");
        assertThat(notification.getDataJson()).containsEntry("route", "/bookings/" + bookingId)
                .containsEntry("bookingId", bookingId).containsEntry("bookingCode", booking.getBookingCode());
        assertThat(notification.getReadAt()).isNull();
        assertThat(notificationsOf(owner)).isEmpty();
    }

    @Test
    @DisplayName("Chu san tu choi: nguoi choi nhan thong bao kem ly do")
    void rejectedNotifiesPlayerWithReason() throws Exception {
        String bookingId = newBooking();
        mockMvcPost("/api/v1/owner/booking-requests/" + bookingId + "/reject", ownerToken, "{\"reasonCode\":\"other\",\"note\":\"San dang bao tri he thong den\"}", 200);

        List<Notification> sent = notificationsOf(player);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getType()).isEqualTo("booking_confirmed");
        assertThat(sent.getFirst().getTitle()).isEqualTo("Chủ sân đã từ chối đặt sân");
        assertThat(sent.getFirst().getContent()).contains("San dang bao tri he thong den");
        assertThat(sent.getFirst().getDataJson()).containsEntry("route", "/bookings/" + bookingId);
        assertThat(notificationsOf(owner)).isEmpty();
    }

    @Test
    @DisplayName("Chu san xac nhan da thu tien: nguoi choi nhan them thong bao; ba moc (chap nhan, xac nhan) tao dung hai thong bao, khong lap khi bao thanh toan")
    void paymentConfirmedNotifiesPlayer() throws Exception {
        String bookingId = newBooking();
        accept(bookingId);
        reportPaid(bookingId);
        // Nguoi choi bao da thanh toan khong sinh thong bao booking_confirmed cho chinh minh.
        assertThat(notificationsOf(player, "booking_confirmed")).hasSize(1);

        mockMvcPost("/api/v1/payments/" + paymentOf(bookingId).getId() + "/confirm-received", ownerToken, null, 200);
        List<Notification> sent = notificationsOf(player, "booking_confirmed");
        assertThat(sent).hasSize(2);
        assertThat(sent.getFirst().getTitle()).isEqualTo("Chủ sân đã xác nhận thu tiền");
        assertThat(sent.getFirst().getContent()).contains("Hẹn gặp bạn tại sân");
        assertThat(sent.getFirst().getDataJson()).containsEntry("route", "/bookings/" + bookingId);

        // Xac nhan lan hai bi tu choi (409) va khong tao them thong bao.
        mockMvcPost("/api/v1/payments/" + paymentOf(bookingId).getId() + "/confirm-received", ownerToken, null, 409);
        assertThat(notificationsOf(player, "booking_confirmed")).hasSize(2);
    }

    @Test
    @DisplayName("Booking da tu hoan thanh sau gio choi: chu san xac nhan muon van gui thong bao da thu tien")
    void lateConfirmationOfCompletedBooking() throws Exception {
        String bookingId = newBooking();
        accept(bookingId);
        reportPaid(bookingId);
        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.saveAndFlush(booking);

        mockMvcPost("/api/v1/payments/" + paymentOf(bookingId).getId() + "/confirm-received", ownerToken, null, 200);
        assertThat(notificationsOf(player, "booking_confirmed")).hasSize(2);
        assertThat(notificationsOf(player, "booking_confirmed").getFirst().getTitle()).isEqualTo("Chủ sân đã xác nhận thu tiền");
    }

    @Test
    @DisplayName("Thao tac that bai (booking khong con cho duyet, chu san khac) khong tao thong bao")
    void failedActionsSendNothing() throws Exception {
        String bookingId = newBooking();
        accept(bookingId);
        long before = notificationsOf(player).size();
        mockMvcPost("/api/v1/owner/booking-requests/" + bookingId + "/accept", ownerToken, null, 409);
        mockMvcPost("/api/v1/owner/booking-requests/" + bookingId + "/reject", ownerToken, "{\"reasonCode\":\"maintenance\"}", 409);

        String other = newBooking();
        String strangerToken = loginAs(persistOwner("flow-stranger"));
        mockMvcPost("/api/v1/owner/booking-requests/" + other + "/accept", strangerToken, null, 404);
        mockMvcPost("/api/v1/owner/booking-requests/" + other + "/reject", strangerToken, "{\"reasonCode\":\"maintenance\"}", 404);
        assertThat(notificationsOf(player)).hasSize((int) before);
    }

    @Test
    @DisplayName("Nguoi choi tat kenh trong ung dung cua loai nay: nghiep vu van thanh cong nhung khong co thong bao")
    void respectsPlayerPreference() throws Exception {
        NotificationPreference preference = new NotificationPreference(player, "booking_confirmed");
        preference.setInAppEnabled(false);
        preferenceRepository.saveAndFlush(preference);
        String bookingId = newBooking();
        accept(bookingId);
        assertThat(notificationsOf(player)).isEmpty();
        assertThat(paymentOf(bookingId)).isNotNull();
    }
}
