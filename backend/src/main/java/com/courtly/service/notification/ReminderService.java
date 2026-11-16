package com.courtly.service.notification;

import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.match.Match;
import com.courtly.domain.match.MatchPlayer;
import com.courtly.domain.match.MatchRepository;
import com.courtly.domain.notification.NotificationType;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhac nho dinh ky (2.3.59, 2.3.60).
 *
 * <p>Moi moc nhac chi gui mot lan cho moi (doi tuong, nguoi nhan) nho bang notification_reminders, nen job chay lai hoac chay
 * song song khong gui trung. Khong nhac booking da huy / chua xac nhan, tran da xac nhan, bi bao sai hoac da huy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    /** Moc nhac lich choi: con toi da {@code hours} gio va nhieu hon {@code floorHours} gio. */
    private record BookingSlot(String key, long hours, long floorHours) {
    }

    private static final List<BookingSlot> BOOKING_SLOTS = List.of(new BookingSlot("24h", 24, 2), new BookingSlot("2h", 2, 0));

    /** Tran tu xac nhan sau 72 gio ke tu luc tao (xem MatchService); nhac khi con toi da 48 gio va 12 gio. */
    private static final long AUTO_CONFIRM_HOURS = 72;

    private record MatchSlot(String key, long remainingHours, long floorHours) {
    }

    private static final List<MatchSlot> MATCH_SLOTS = List.of(new MatchSlot("48h", 48, 12), new MatchSlot("12h", 12, 0));

    private final BookingRepository bookingRepository;
    private final MatchRepository matchRepository;
    private final NotificationService notifications;

    /** 2.3.59 - nhac nguoi choi va chu san truoc gio choi cua booking da xac nhan; tra ve so thong bao da gui. */
    @Transactional
    public int remindBookings(Instant now) {
        int sent = 0;
        for (BookingSlot slot : BOOKING_SLOTS) {
            Instant from = now.plus(Duration.ofHours(slot.floorHours()));
            Instant to = now.plus(Duration.ofHours(slot.hours()));
            for (Booking booking : bookingRepository.findConfirmedStartingBetween(from, to)) {
                Instant start = booking.getItems().stream().map(BookingItem::getStartTime).min(Comparator.naturalOrder()).orElse(null);
                if (start == null) {
                    continue;
                }
                String left = remaining(now, start);
                String when = BookingNotifier.NotificationText.vietnamTime(start);
                String court = booking.getItems().getFirst().getCourt().getName();
                Map<String, Object> extra = BookingNotifier.extra(booking);
                if (notifications.sendReminderOnce(booking.getUser().getId(), NotificationType.BOOKING_REMINDER, booking.getId(), slot.key(),
                        "Nhắc lịch chơi sắp tới",
                        "Bạn có lịch chơi tại %s, %s, lúc %s (%s). Mã booking %s.".formatted(booking.getVenue().getName(), court, when, left,
                                booking.getBookingCode()),
                        "/bookings/" + booking.getId(), extra)) {
                    sent++;
                }
                if (notifications.sendReminderOnce(booking.getVenue().getOwner().getId(), NotificationType.BOOKING_REMINDER, booking.getId(),
                        slot.key(), "Booking sắp diễn ra",
                        "%s đã đặt %s, %s lúc %s (%s). Mã booking %s.".formatted(booking.getUser().getFullName(), booking.getVenue().getName(),
                                court, when, left, booking.getBookingCode()),
                        "/owner/bookings", extra)) {
                    sent++;
                }
            }
        }
        if (sent > 0) {
            log.info("Da gui {} nhac nho lich choi", sent);
        }
        return sent;
    }

    /**
     * 2.3.60 - nhac nguoi choi chua xac nhan ket qua truoc khi tran tu xac nhan; tra ve so thong bao da gui.
     * Nguoi da xac nhan hoac da bao sai khong bi nhac.
     */
    @Transactional
    public int remindMatches(Instant now) {
        int sent = 0;
        for (MatchSlot slot : MATCH_SLOTS) {
            // Tran tao trong (now - 72h, now - (72h - remaining)] con toi da `remaining` gio nhung nhieu hon `floor` gio.
            Instant from = now.minus(Duration.ofHours(AUTO_CONFIRM_HOURS - slot.floorHours()));
            Instant to = now.minus(Duration.ofHours(AUTO_CONFIRM_HOURS - slot.remainingHours()));
            for (Match match : matchRepository.findAwaitingConfirmationCreatedBetween(from, to)) {
                long minutesLeft = Duration.between(now, match.getCreatedAt().plus(Duration.ofHours(AUTO_CONFIRM_HOURS))).toMinutes();
                long hoursLeft = Math.max(Math.round(minutesLeft / 60.0), 1);
                for (MatchPlayer player : match.getPlayers()) {
                    if (player.getResultConfirmedAt() != null || player.getResultDisputedAt() != null) {
                        continue;
                    }
                    Map<String, Object> extra = new LinkedHashMap<>();
                    extra.put("matchId", match.getId().toString());
                    if (notifications.sendReminderOnce(player.getUser().getId(), NotificationType.MATCH_REMINDER, match.getId(), slot.key(),
                            "Nhắc xác nhận kết quả trận đấu",
                            "Kết quả trận đấu của bạn đang chờ xác nhận và sẽ tự động được xác nhận sau khoảng %d giờ nếu không có phản hồi. Hãy xác nhận hoặc báo sai."
                                    .formatted(hoursLeft),
                            "/matches/" + match.getId(), extra)) {
                        sent++;
                    }
                }
            }
        }
        if (sent > 0) {
            log.info("Da gui {} nhac nho xac nhan tran dau", sent);
        }
        return sent;
    }

    /** "con khoang 5 gio" / "con khoang 40 phut". */
    private static String remaining(Instant now, Instant start) {
        long minutes = Math.max(Duration.between(now, start).toMinutes(), 1);
        return minutes >= 90 ? "còn khoảng " + Math.round(minutes / 60.0) + " giờ" : "còn khoảng " + minutes + " phút";
    }
}
