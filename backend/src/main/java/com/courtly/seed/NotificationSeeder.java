package com.courtly.seed;

import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationRepository;
import com.courtly.domain.notification.NotificationType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buoc 9: notifications.
 *
 * <p>notification_preferences da duoc tao cho tung tai khoan trong {@link UserSeeder}.
 */
@Slf4j
@Component
@Order(9)
@RequiredArgsConstructor
public class NotificationSeeder implements Seeder {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * @param hoursAgo     thoi diem tao, tinh nguoc tu bay gio
     * @param readHoursAgo null nghia la chua doc
     */
    private record NotificationSeed(String key, String userKey, String type, String title,
                                    String content, int hoursAgo, Integer readHoursAgo,
                                    Map<String, Object> data) {
    }

    private static final List<NotificationSeed> NOTIFICATIONS = List.of(
            new NotificationSeed("notif-01", "player-01", NotificationType.BOOKING_CONFIRMED,
                    "Dat san thanh cong",
                    "Don CT-20260924-0001 tai San Cau Long Hai Chau da duoc xac nhan.",
                    47, 46, Map.of("bookingCode", "CT-20260924-0001", "route", "/bookings")),
            new NotificationSeed("notif-02", "player-01", NotificationType.BOOKING_REMINDER,
                    "Nhac lich choi ngay mai",
                    "18:00 ngay mai ban co lich tai San Cau Long Hai Chau, san A.",
                    3, null, Map.of("bookingCode", "CT-20260924-0001", "route", "/bookings")),
            new NotificationSeed("notif-03", "player-02", NotificationType.PAYMENT_STATUS,
                    "Cho thanh toan",
                    "Don CT-20260924-0002 se het han sau 10 phut neu chua chuyen khoan.",
                    1, null, Map.of("bookingCode", "CT-20260924-0002", "status", "pending")),
            new NotificationSeed("notif-04", "player-04", NotificationType.BOOKING_CANCELLED,
                    "Da huy dat san",
                    "Don CT-20260918-0004 da duoc huy, tien se hoan ve trong 1-3 ngay lam viec.",
                    120, 118, Map.of("bookingCode", "CT-20260918-0004", "refund", true)),
            new NotificationSeed("notif-05", "player-05", NotificationType.PAYMENT_STATUS,
                    "Don dat san da het han",
                    "Don CT-20260921-0005 het han thanh toan. Ban co the chon lich khac.",
                    48, 40, Map.of("bookingCode", "CT-20260921-0005", "status", "expired")),
            new NotificationSeed("notif-06", "player-06", NotificationType.PARTNER_REQUEST,
                    "Loi moi ghep cap moi",
                    "Nguyen Van An muon ghep cap danh doi voi ban.",
                    216, 214, Map.of("requesterKey", "player-01", "route", "/partners/requests")),
            new NotificationSeed("notif-07", "player-01", NotificationType.PARTNER_REQUEST_RESPONSE,
                    "Loi moi ghep cap duoc chap nhan",
                    "Vu Ngoc Ha da chap nhan loi moi ghep cap cua ban.",
                    213, null, Map.of("partnerKey", "player-06", "route", "/partners")),
            new NotificationSeed("notif-08", "player-05", NotificationType.PARTNER_REQUEST,
                    "Loi moi ghep cap moi",
                    "Tran Thi Binh muon ghep cap danh doi nam nu voi ban.",
                    48, null, Map.of("requesterKey", "player-02", "route", "/partners/requests")),
            new NotificationSeed("notif-09", "player-03", NotificationType.MATCH_SCHEDULED,
                    "Tran dau da duoc tao",
                    "Tran doi tai San Cau Long Cam Le da duoc len lich.",
                    120, 119, Map.of("matchKey", "match-06")),
            new NotificationSeed("notif-10", "player-06", NotificationType.MATCH_REMINDER,
                    "Nhac tran dau",
                    "Tran dau cua ban bat dau sau 2 gio nua.",
                    74, null, Map.of("matchKey", "match-06")),
            new NotificationSeed("notif-11", "owner-01", NotificationType.BOOKING_CONFIRMED,
                    "Co don dat san moi",
                    "Don CT-20260925-0006 tai San Cau Long Hai Chau da thanh toan thanh cong.",
                    40, null, Map.of("bookingCode", "CT-20260925-0006")),
            new NotificationSeed("notif-12", "owner-02", NotificationType.PAYMENT_STATUS,
                    "Yeu cau rut tien dang cho duyet",
                    "Yeu cau rut 126.000d cua ban dang cho quan tri vien duyet.",
                    24, null, Map.of("amount", 126000, "status", "pending")));

    @Override
    public String name() {
        return "notifications";
    }

    @Override
    @Transactional
    public void seed() {
        if (notificationRepository.count() > 0) {
            log.info("  [notifications] da co du lieu, bo qua");
            return;
        }

        for (NotificationSeed seed : NOTIFICATIONS) {
            Notification notification = new Notification();
            notification.setId(SeedIds.of("notification:" + seed.key()));
            notification.setUser(user(seed.userKey()));
            notification.setType(seed.type());
            notification.setTitle(seed.title());
            notification.setContent(seed.content());
            notification.setDataJson(seed.data());
            notification.setCreatedAt(Instant.now().minus(seed.hoursAgo(), ChronoUnit.HOURS));
            notification.setReadAt(seed.readHoursAgo() == null ? null
                    : Instant.now().minus(seed.readHoursAgo(), ChronoUnit.HOURS));
            notificationRepository.save(notification);
        }

        long unread = NOTIFICATIONS.stream().filter(n -> n.readHoursAgo() == null).count();
        log.info("  [notifications] {} thong bao, {} chua doc", NOTIFICATIONS.size(), unread);
    }

    private User user(String key) {
        return userRepository.findById(SeedIds.of("user:" + key))
                .orElseThrow(() -> new IllegalStateException("Thieu tai khoan '" + key + "'"));
    }
}
