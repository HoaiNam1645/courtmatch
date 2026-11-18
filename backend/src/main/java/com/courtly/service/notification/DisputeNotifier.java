package com.courtly.service.notification;

import com.courtly.domain.notification.NotificationType;
import com.courtly.domain.payment.BookingDispute;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Thong bao ve bao cao don dat san (2.3.85). Goi ben trong transaction cua nghiep vu. */
@Component
@RequiredArgsConstructor
public class DisputeNotifier {

    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final NotificationService notifications;

    /** Nguoi choi bo sung thong tin khi bao cao con cho chu san giai trinh: bao cho chu san. */
    public void updated(BookingDispute dispute) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("disputeId", dispute.getId().toString());
        data.put("bookingId", dispute.getBooking().getId().toString());
        notifications.send(dispute.getBooking().getVenue().getOwner().getId(), NotificationType.BOOKING_DISPUTE_UPDATED,
                "Người chơi bổ sung thông tin báo cáo",
                "Người chơi vừa bổ sung thông tin cho báo cáo về đơn %s. Hãy xem trước hạn giải trình %s."
                        .formatted(dispute.getBooking().getBookingCode(), DEADLINE.format(dispute.getOwnerResponseDeadline())),
                "/owner/bookings", data);
    }

    /** Nguoi choi tao bao cao: bao cho chu san cua san va han giai trinh. */
    public void opened(BookingDispute dispute, String categoryLabel) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("disputeId", dispute.getId().toString());
        data.put("bookingId", dispute.getBooking().getId().toString());
        data.put("category", dispute.getCategory());
        notifications.send(dispute.getBooking().getVenue().getOwner().getId(), NotificationType.BOOKING_DISPUTE_OPENED,
                "Có báo cáo mới về đơn đặt sân",
                "Người chơi báo cáo đơn %s tại %s (%s). Hãy giải trình trước %s."
                        .formatted(dispute.getBooking().getBookingCode(), dispute.getBooking().getVenue().getName(), categoryLabel,
                                DEADLINE.format(dispute.getOwnerResponseDeadline())),
                "/owner/bookings", data);
    }
}
