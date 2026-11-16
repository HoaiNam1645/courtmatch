package com.courtly.service.notification;

import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.notification.NotificationType;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Thong bao ve dat san va thanh toan (2.3.53 - 2.3.55).
 *
 * <p>Tach rieng khoi BookingService / PaymentService de nghiep vu chi goi mot dong va noi dung thong bao nam mot cho.
 * Goi ben trong transaction cua nghiep vu.
 */
@Component
@RequiredArgsConstructor
public class BookingNotifier {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final NotificationService notifications;

    /** 2.3.53 - chu san chap nhan yeu cau dat san: nguoi choi can thanh toan truc tiep. */
    public void accepted(Booking booking, int paymentWindowMinutes) {
        notifications.send(booking.getUser().getId(), NotificationType.BOOKING_CONFIRMED, "Chủ sân đã chấp nhận đặt sân",
                "Booking %s tại %s lúc %s đã được chủ sân chấp nhận. Vui lòng thanh toán trực tiếp cho chủ sân trong %d phút."
                        .formatted(booking.getBookingCode(), booking.getVenue().getName(), when(booking), paymentWindowMinutes),
                "/bookings/" + booking.getId(), extra(booking));
    }

    /** 2.3.53 - chu san tu choi yeu cau dat san. */
    public void rejected(Booking booking, String reason) {
        notifications.send(booking.getUser().getId(), NotificationType.BOOKING_CONFIRMED, "Chủ sân đã từ chối đặt sân",
                "Booking %s tại %s lúc %s bị chủ sân từ chối. Lý do: %s."
                        .formatted(booking.getBookingCode(), booking.getVenue().getName(), when(booking), reason),
                "/bookings/" + booking.getId(), extra(booking));
    }

    /** 2.3.53 - chu san xac nhan da thu tien: booking duoc xac nhan. */
    public void paymentConfirmed(Booking booking) {
        notifications.send(booking.getUser().getId(), NotificationType.BOOKING_CONFIRMED, "Chủ sân đã xác nhận thu tiền",
                "Chủ sân đã xác nhận nhận tiền cho booking %s tại %s lúc %s. Hẹn gặp bạn tại sân!"
                        .formatted(booking.getBookingCode(), booking.getVenue().getName(), when(booking)),
                "/bookings/" + booking.getId(), extra(booking));
    }

    static Map<String, Object> extra(Booking booking) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bookingId", booking.getId().toString());
        data.put("bookingCode", booking.getBookingCode());
        return data;
    }

    /** Gio bat dau som nhat cua booking, theo gio Viet Nam; booking khong co khung gio thi tra ve dau gach. */
    static String when(Booking booking) {
        return booking.getItems().stream().map(BookingItem::getStartTime).min(Comparator.naturalOrder())
                .map(NotificationText::vietnamTime).orElse("—");
    }

    /** Dinh dang thoi gian dung chung. */
    static final class NotificationText {
        private NotificationText() {
        }

        static String vietnamTime(Instant instant) {
            return WHEN.format(instant.atZone(ZONE));
        }
    }
}
