package com.courtly.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationPreference;
import com.courtly.domain.notification.NotificationPreferenceRepository;
import com.courtly.service.booking.BookingService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 2.3.54 - Thong bao huy dat san: nguoi choi huy (bao chu san) va booking het han (bao nguoi choi). */
class BookingCancelledNotificationTest extends NotificationFlowTestBase {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingService bookingService;
    @Autowired private NotificationPreferenceRepository preferenceRepository;

    private void cancel(String bookingId, String reason, int expected) throws Exception {
        mockMvcPost("/api/v1/bookings/" + bookingId + "/cancel", playerToken, "{\"reason\":" + (reason == null ? "null" : "\"" + reason + "\"") + "}", expected);
    }

    private Booking booking(String id) {
        return bookingRepository.findById(UUID.fromString(id)).orElseThrow();
    }

    @Test
    @DisplayName("Nguoi choi huy yeu cau dang cho chu san: chu san nhan thong bao booking_cancelled co ten nguoi choi, ma booking, san, gio, ly do va route; nguoi choi khong nhan")
    void playerCancelsPendingRequest() throws Exception {
        String bookingId = newBooking();
        cancel(bookingId, "Doi lich cong tac dot xuat", 200);

        List<Notification> sent = notificationsOf(owner);
        assertThat(sent).hasSize(1);
        Notification notification = sent.getFirst();
        assertThat(notification.getType()).isEqualTo("booking_cancelled");
        assertThat(notification.getTitle()).isEqualTo("Người chơi đã hủy đặt sân");
        assertThat(notification.getContent()).contains(player.getFullName()).contains(booking(bookingId).getBookingCode())
                .contains("San Thong Bao").contains("Lý do: Doi lich cong tac dot xuat").containsPattern("\\d{2}:\\d{2} \\d{2}/\\d{2}/\\d{4}");
        assertThat(notification.getDataJson()).containsEntry("route", "/owner/bookings").containsEntry("bookingId", bookingId);
        assertThat(notificationsOf(player)).isEmpty();
    }

    @Test
    @DisplayName("Huy khi cho thanh toan va khi da xac nhan deu bao chu san; thieu ly do bi tu choi 400 va khong co thong bao")
    void cancelAtEveryCancellableStage() throws Exception {
        String awaiting = newBooking();
        accept(awaiting);
        // Ly do huy bat buoc: thieu hoac chi co khoang trang thi bi tu choi va khong co thong bao.
        cancel(awaiting, null, 400);
        cancel(awaiting, "   ", 400);
        assertThat(notificationsOf(owner)).isEmpty();
        cancel(awaiting, "Doi y khong choi nua", 200);
        assertThat(notificationsOf(owner)).hasSize(1);
        assertThat(notificationsOf(owner).getFirst().getContent()).contains("Lý do: Doi y khong choi nua");

        String confirmed = newBooking();
        accept(confirmed);
        reportPaid(confirmed);
        mockMvcPost("/api/v1/payments/" + paymentOf(confirmed).getId() + "/confirm-received", ownerToken, null, 200);
        cancel(confirmed, "Bi om khong choi duoc", 200);
        assertThat(notificationsOf(owner)).hasSize(2);
        assertThat(notificationsOf(owner).getFirst().getContent()).contains("Bi om khong choi duoc");
    }

    @Test
    @DisplayName("Huy khong duoc (da huy truoc do) khong tao them thong bao; nguoi khac huy don khong phai cua minh 404 khong co thong bao")
    void failedCancellationsSendNothing() throws Exception {
        String bookingId = newBooking();
        cancel(bookingId, "Huy lan dau", 200);
        cancel(bookingId, "Huy lan hai", 409);
        assertThat(notificationsOf(owner)).hasSize(1);

        String other = newBooking();
        mockMvcPost("/api/v1/bookings/" + other + "/cancel", loginAs(persistUser("flow-intruder", com.courtly.domain.account.Role.PLAYER)),
                "{\"reason\":\"Khong phai don cua toi\"}", 404);
        assertThat(notificationsOf(owner)).hasSize(1);
    }

    @Test
    @DisplayName("Chu san khong phan hoi dung han: nguoi choi nhan thong bao het han (chu san khong nhan); chay lai job khong gui trung")
    void ownerResponseExpired() throws Exception {
        String bookingId = newBooking();
        Booking booking = booking(bookingId);
        booking.setOwnerResponseExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        bookingRepository.saveAndFlush(booking);

        assertThat(bookingService.expireOverdueBookings()).isEqualTo(1);
        List<Notification> sent = notificationsOf(player);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getType()).isEqualTo("booking_cancelled");
        assertThat(sent.getFirst().getTitle()).isEqualTo("Yêu cầu đặt sân đã hết hạn");
        assertThat(sent.getFirst().getContent()).contains(booking.getBookingCode()).contains("chủ sân không phản hồi đúng hạn")
                .contains("đặt lại");
        assertThat(sent.getFirst().getDataJson()).containsEntry("route", "/bookings/" + bookingId);
        assertThat(notificationsOf(owner)).isEmpty();

        assertThat(bookingService.expireOverdueBookings()).isZero();
        assertThat(notificationsOf(player)).hasSize(1);
    }

    @Test
    @DisplayName("Qua han thanh toan: nguoi choi nhan thong bao; da bao thanh toan (cho chu san xac nhan) thi khong bi het han va khong co thong bao")
    void paymentExpired() throws Exception {
        String unpaid = newBooking();
        accept(unpaid);
        String reported = newBooking();
        accept(reported);
        reportPaid(reported);
        long before = notificationsOf(player).size();
        for (String id : List.of(unpaid, reported)) {
            Booking booking = booking(id);
            booking.setPaymentExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES));
            bookingRepository.saveAndFlush(booking);
        }

        assertThat(bookingService.expireOverdueBookings()).isEqualTo(1);
        List<Notification> sent = notificationsOf(player, "booking_cancelled");
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getTitle()).isEqualTo("Đặt sân đã hết hạn thanh toán");
        assertThat(sent.getFirst().getContent()).contains("chưa thanh toán trong thời hạn");
        assertThat(sent.getFirst().getDataJson()).containsEntry("bookingId", unpaid);
        assertThat(notificationsOf(player)).hasSize((int) before + 1);
    }

    @Test
    @DisplayName("Chu san tat loai thong bao nay: nguoi choi van huy duoc nhung chu san khong nhan thong bao")
    void respectsOwnerPreference() throws Exception {
        NotificationPreference preference = new NotificationPreference(owner, "booking_cancelled");
        preference.setInAppEnabled(false);
        preferenceRepository.saveAndFlush(preference);
        String bookingId = newBooking();
        cancel(bookingId, "Huy thu", 200);
        assertThat(notificationsOf(owner)).isEmpty();
        assertThat(booking(bookingId).getCancelledAt()).isNotNull();
    }
}
