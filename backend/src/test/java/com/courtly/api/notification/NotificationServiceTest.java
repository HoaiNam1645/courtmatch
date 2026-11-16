package com.courtly.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.courtly.api.owner.OwnerApiTestBase;
import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationPreference;
import com.courtly.domain.notification.NotificationPreferenceRepository;
import com.courtly.domain.notification.NotificationRepository;
import com.courtly.domain.notification.NotificationType;
import com.courtly.service.mail.NotificationEmailRequested;
import com.courtly.service.notification.NotificationService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/** Nen tang thong bao: dich vu gui, ton trong tuy chon, email, nhac mot lan. */
@RecordApplicationEvents
class NotificationServiceTest extends OwnerApiTestBase {

    @Autowired private NotificationService service;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationPreferenceRepository preferenceRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ApplicationEvents events;

    private User player;

    @BeforeEach
    void setUp() {
        ensureRoles();
        player = persistUser("notify-player", Role.PLAYER);
    }

    private long emails() {
        return events.stream(NotificationEmailRequested.class).count();
    }

    private void preference(User user, String type, boolean inApp, boolean email) {
        NotificationPreference preference = new NotificationPreference(user, type);
        preference.setInAppEnabled(inApp);
        preference.setEmailEnabled(email);
        preferenceRepository.saveAndFlush(preference);
    }

    @Test
    @DisplayName("Gui thong bao: luu trong ung dung chua doc kem metadata dieu huong va phat su kien email; nguoi dung chua co tuy chon duoc coi la bat ca hai kenh")
    void sendsInAppAndEmailByDefault() {
        Optional<Notification> sent = service.send(player.getId(), NotificationType.BOOKING_CONFIRMED, "Dat san thanh cong",
                "Don CT-1 da duoc chap nhan.", "/bookings/abc", Map.of("bookingCode", "CT-1"));
        assertThat(sent).isPresent();
        Notification saved = notificationRepository.findById(sent.get().getId()).orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(player.getId());
        assertThat(saved.getType()).isEqualTo(NotificationType.BOOKING_CONFIRMED);
        assertThat(saved.getTitle()).isEqualTo("Dat san thanh cong");
        assertThat(saved.getContent()).isEqualTo("Don CT-1 da duoc chap nhan.");
        assertThat(saved.getReadAt()).isNull();
        assertThat(saved.getDataJson()).containsEntry("route", "/bookings/abc").containsEntry("bookingCode", "CT-1");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(emails()).isEqualTo(1);
        NotificationEmailRequested email = events.stream(NotificationEmailRequested.class).findFirst().orElseThrow();
        assertThat(email.email()).isEqualTo(player.getEmail());
        assertThat(email.title()).isEqualTo("Dat san thanh cong");
    }

    @Test
    @DisplayName("Tat kenh trong ung dung thi khong luu; tat email thi khong phat su kien; tat ca hai thi khong co gi")
    void respectsPreferences() {
        preference(player, NotificationType.BOOKING_CONFIRMED, false, true);
        assertThat(service.send(player.getId(), NotificationType.BOOKING_CONFIRMED, "A", "a", Map.of())).isEmpty();
        assertThat(emails()).isEqualTo(1);

        preference(player, NotificationType.PAYMENT_STATUS, true, false);
        assertThat(service.send(player.getId(), NotificationType.PAYMENT_STATUS, "B", "b", Map.of())).isPresent();
        assertThat(emails()).isEqualTo(1);

        preference(player, NotificationType.BOOKING_CANCELLED, false, false);
        assertThat(service.send(player.getId(), NotificationType.BOOKING_CANCELLED, "C", "c", Map.of())).isEmpty();
        assertThat(emails()).isEqualTo(1);
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(player.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Loai bat buoc luon co thong bao trong ung dung du da tat tuy chon; tuy chon cua loai khac khong anh huong")
    void mandatoryTypeIgnoresOptOut() {
        preference(player, NotificationType.DEBT_RESTRICTION, false, false);
        assertThat(service.send(player.getId(), NotificationType.DEBT_RESTRICTION, "Han che", "x", Map.of())).isPresent();
        assertThat(emails()).isZero();
    }

    @Test
    @DisplayName("Khong gui cho tai khoan khong hoat dong, khong ton tai hoac thieu id; thieu email thi chi co thong bao trong ung dung")
    void skipsUnavailableRecipients() {
        User banned = persistUser("notify-banned", Role.PLAYER);
        banned.setStatus(UserStatus.BANNED);
        userRepository.saveAndFlush(banned);
        assertThat(service.send(banned.getId(), NotificationType.BOOKING_CONFIRMED, "A", "a", Map.of())).isEmpty();
        assertThat(service.send(UUID.randomUUID(), NotificationType.BOOKING_CONFIRMED, "A", "a", Map.of())).isEmpty();
        assertThat(service.send(null, NotificationType.BOOKING_CONFIRMED, "A", "a", Map.of())).isEmpty();

        User phoneOnly = persistUser("notify-phone", Role.PLAYER);
        phoneOnly.setEmail(null);
        phoneOnly.setPhone("0905" + (int) (Math.random() * 900000 + 100000));
        userRepository.saveAndFlush(phoneOnly);
        assertThat(service.send(phoneOnly.getId(), NotificationType.BOOKING_CONFIRMED, "A", "a", Map.of())).isPresent();
        assertThat(emails()).isZero();
    }

    @Test
    @DisplayName("Nhac nho chi gui mot lan cho moi (loai, doi tuong, moc, nguoi nhan); moc khac hoac nguoi nhan khac van gui")
    void reminderIsSentOnce() {
        UUID booking = UUID.randomUUID();
        User other = persistUser("notify-other", Role.PLAYER);
        assertThat(service.sendReminderOnce(player.getId(), NotificationType.BOOKING_REMINDER, booking, "24h", "Nhac", "n", "/bookings", Map.of()))
                .isTrue();
        assertThat(service.sendReminderOnce(player.getId(), NotificationType.BOOKING_REMINDER, booking, "24h", "Nhac", "n", "/bookings", Map.of()))
                .isFalse();
        assertThat(service.sendReminderOnce(player.getId(), NotificationType.BOOKING_REMINDER, booking, "2h", "Nhac", "n", "/bookings", Map.of()))
                .isTrue();
        assertThat(service.sendReminderOnce(other.getId(), NotificationType.BOOKING_REMINDER, booking, "24h", "Nhac", "n", "/bookings", Map.of()))
                .isTrue();
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(player.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_reminders WHERE ref_id = ?", Long.class, booking)).isEqualTo(3);
    }
}
