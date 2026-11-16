package com.courtly.service.notification;

import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.common.enums.UserStatus;
import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationPreference;
import com.courtly.domain.notification.NotificationPreferenceId;
import com.courtly.domain.notification.NotificationPreferenceRepository;
import com.courtly.domain.notification.NotificationRepository;
import com.courtly.domain.notification.NotificationType;
import com.courtly.service.mail.NotificationEmailRequested;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gui thong bao cho nguoi dung (2.3.53 - 2.3.60).
 *
 * <p>Goi ben trong transaction cua nghiep vu: nghiep vu rollback thi thong bao cung khong con, nen khong co thong bao ve mot
 * viec chua tung xay ra. Email gui bat dong bo sau khi commit (xem MailService), loi gui khong anh huong nghiep vu.
 *
 * <p>Ton trong notification_preferences cua nguoi nhan: nguoi dung chua co ban ghi duoc coi la bat ca kenh trong ung dung va
 * email. Loai bat buoc ({@link NotificationType#MANDATORY}) luon co thong bao trong ung dung.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final JdbcTemplate jdbc;

    /**
     * Gui mot thong bao.
     *
     * @param data metadata dieu huong, vi du route va ma booking; null = khong co
     * @return thong bao trong ung dung da tao; rong neu nguoi nhan tat kenh nay, tai khoan khong con hoat dong hoac khong ton tai
     */
    @Transactional
    public Optional<Notification> send(UUID userId, String type, String title, String content, Map<String, Object> data) {
        if (userId == null) {
            return Optional.empty();
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return Optional.empty();
        }
        NotificationPreference preference = preferenceRepository.findById(new NotificationPreferenceId(userId, type)).orElse(null);
        boolean inApp = preference == null || preference.isInAppEnabled() || NotificationType.MANDATORY.contains(type);
        boolean email = (preference == null || preference.isEmailEnabled()) && user.getEmail() != null && !user.getEmail().isBlank();

        Notification created = null;
        if (inApp) {
            created = new Notification();
            created.setId(UUID.randomUUID());
            created.setUser(user);
            created.setType(type);
            created.setTitle(title);
            created.setContent(content);
            created.setDataJson(data == null ? null : new LinkedHashMap<>(data));
            created.setCreatedAt(Instant.now());
            notificationRepository.save(created);
        }
        if (email) {
            events.publishEvent(new NotificationEmailRequested(user.getEmail(), user.getFullName(), title, content));
        }
        return Optional.ofNullable(created);
    }

    /** Thong bao co dieu huong den mot man hinh: tien loi cho cac cho goi. */
    public Optional<Notification> send(UUID userId, String type, String title, String content, String route, Map<String, Object> extra) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (extra != null) {
            data.putAll(extra);
        }
        data.put("route", route);
        return send(userId, type, title, content, data);
    }

    /**
     * Gui nhac nho mot lan cho moi (loai, doi tuong, moc, nguoi nhan): job chay lai khong gui trung.
     *
     * @return true neu vua gui, false neu da gui truoc do
     */
    @Transactional
    public boolean sendReminderOnce(UUID userId, String type, UUID refId, String slot, String title, String content,
                                    String route, Map<String, Object> extra) {
        int inserted = jdbc.update("""
                INSERT INTO notification_reminders (type, ref_id, slot, user_id) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, type, refId, slot, userId);
        if (inserted == 0) {
            return false;
        }
        send(userId, type, title, content, route, extra);
        return true;
    }
}
