package com.courtly.api.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationPreference;
import com.courtly.domain.notification.NotificationPreferenceRepository;
import com.courtly.service.notification.ReminderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 2.3.59 - Nhac nho lich choi: nguoi choi va chu san, moi moc mot lan, chi booking da xac nhan sap toi gio choi. */
class BookingReminderTest extends NotificationFlowTestBase {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private ReminderService reminderService;
    @Autowired private NotificationPreferenceRepository preferenceRepository;
    @PersistenceContext private EntityManager entityManager;

    /** Booking da xac nhan, gio choi bat dau sau {@code minutes} phut. */
    private Booking confirmedStartingIn(long minutes) throws Exception {
        String bookingId = newBooking();
        accept(bookingId);
        reportPaid(bookingId);
        mockMvcPost("/api/v1/payments/" + paymentOf(bookingId).getId() + "/confirm-received", ownerToken, null, 200);
        return moveTo(bookingId, minutes);
    }

    private Booking moveTo(String bookingId, long minutes) {
        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        Instant start = Instant.now().plus(minutes, ChronoUnit.MINUTES);
        for (BookingItem item : booking.getItems()) {
            item.setStartTime(start);
            item.setEndTime(start.plus(90, ChronoUnit.MINUTES));
        }
        bookingRepository.saveAndFlush(booking);
        entityManager.clear();
        return booking;
    }

    private List<Notification> reminders(com.courtly.domain.account.User user) {
        return notificationsOf(user, "booking_reminder");
    }

    @Test
    @DisplayName("Booking da xac nhan con 5 gio: nguoi choi va chu san moi nguoi nhan mot nhac nho co san, gio, thoi gian con lai, ma booking va route; chay lai khong gui trung")
    void remindsPlayerAndOwnerOnce() throws Exception {
        Booking booking = confirmedStartingIn(300);
        assertThat(reminderService.remindBookings(Instant.now())).isEqualTo(2);

        List<Notification> forPlayer = reminders(player);
        assertThat(forPlayer).hasSize(1);
        assertThat(forPlayer.getFirst().getTitle()).isEqualTo("Nhắc lịch chơi sắp tới");
        assertThat(forPlayer.getFirst().getContent()).contains("San Thong Bao").contains("còn khoảng 5 giờ").contains(booking.getBookingCode())
                .containsPattern("\\d{2}:\\d{2} \\d{2}/\\d{2}/\\d{4}");
        assertThat(forPlayer.getFirst().getDataJson()).containsEntry("route", "/bookings/" + booking.getId());

        List<Notification> forOwner = reminders(owner);
        assertThat(forOwner).hasSize(1);
        assertThat(forOwner.getFirst().getTitle()).isEqualTo("Booking sắp diễn ra");
        assertThat(forOwner.getFirst().getContent()).contains(player.getFullName()).contains(booking.getBookingCode());
        assertThat(forOwner.getFirst().getDataJson()).containsEntry("route", "/owner/bookings");

        assertThat(reminderService.remindBookings(Instant.now())).isZero();
        assertThat(reminders(player)).hasSize(1);
        assertThat(reminders(owner)).hasSize(1);
    }

    @Test
    @DisplayName("Moc 24 gio roi moc 2 gio: cung mot booking duoc nhac hai lan (gio khac nhau), moi moc dung mot lan; con duoi 90 phut thi noi bang phut")
    void secondSlotIsSeparate() throws Exception {
        Booking booking = confirmedStartingIn(300);
        Instant now = Instant.now();
        assertThat(reminderService.remindBookings(now)).isEqualTo(2);
        // 4 gio sau: con khoang 1 gio -> moc 2 gio.
        assertThat(reminderService.remindBookings(now.plus(4, ChronoUnit.HOURS).plus(15, ChronoUnit.MINUTES))).isEqualTo(2);
        assertThat(reminders(player)).hasSize(2);
        assertThat(reminders(player)).extracting(Notification::getContent).anyMatch(text -> text.contains("còn khoảng 5 giờ"))
                .anyMatch(text -> text.contains("phút"));
        assertThat(reminders(owner)).hasSize(2);
        assertThat(reminderService.remindBookings(now.plus(4, ChronoUnit.HOURS).plus(20, ChronoUnit.MINUTES))).isZero();
        assertThat(booking.getId()).isNotNull();
    }

    @Test
    @DisplayName("Chi nhac booking da xac nhan va nam trong cua so: khong nhac booking xa hon 24 gio, da qua gio, cho thanh toan, da huy, da hoan thanh")
    void remindsOnlyEligibleBookings() throws Exception {
        // Moi booking mot khung gio rieng de khong vi pham rang buoc khong trung gio cua san.
        confirmedStartingIn(25 * 60);       // qua xa
        confirmedStartingIn(-30);           // da bat dau
        String awaiting = newBooking();
        accept(awaiting);
        moveTo(awaiting, 120);              // chua xac nhan thanh toan
        Booking cancelled = confirmedStartingIn(300);
        mockMvcPost("/api/v1/bookings/" + cancelled.getId() + "/cancel", playerToken, "{\"reason\":\"Doi lich\"}", 200);
        Booking completed = confirmedStartingIn(480);
        Booking reloaded = bookingRepository.findById(completed.getId()).orElseThrow();
        reloaded.setStatus(BookingStatus.COMPLETED);
        bookingRepository.saveAndFlush(reloaded);
        entityManager.clear();

        long beforePlayer = reminders(player).size();
        assertThat(reminderService.remindBookings(Instant.now())).isZero();
        assertThat(reminders(player)).hasSize((int) beforePlayer);
        assertThat(reminders(owner)).isEmpty();
    }

    @Test
    @DisplayName("Moc ranh gioi: dung 24 gio duoc nhac, dung 2 gio thuoc moc 2 gio, qua 24 gio mot chut thi chua")
    void windowBoundaries() throws Exception {
        confirmedStartingIn(24 * 60 - 1);
        assertThat(reminderService.remindBookings(Instant.now())).isEqualTo(2);
        assertThat(reminders(player).getFirst().getContent()).contains("còn khoảng 24 giờ");

        Booking later = confirmedStartingIn(24 * 60 + 120);
        assertThat(reminderService.remindBookings(Instant.now())).isZero();
        // Hai tieng sau booking nay lot vao cua so 24 gio.
        assertThat(reminderService.remindBookings(Instant.now().plus(140, ChronoUnit.MINUTES))).isEqualTo(2);
        assertThat(later.getId()).isNotNull();
    }

    @Test
    @DisplayName("Tuy chon duoc ton trong: nguoi choi tat booking_reminder thi chi chu san nhan; mot moc da gui thi khong gui lai du bat lai")
    void respectsPreferences() throws Exception {
        NotificationPreference preference = new NotificationPreference(player, "booking_reminder");
        preference.setInAppEnabled(false);
        preferenceRepository.saveAndFlush(preference);
        confirmedStartingIn(120);
        reminderService.remindBookings(Instant.now());
        assertThat(reminders(player)).isEmpty();
        assertThat(reminders(owner)).hasSize(1);

        preference.setInAppEnabled(true);
        preferenceRepository.saveAndFlush(preference);
        assertThat(reminderService.remindBookings(Instant.now())).isZero();
        assertThat(reminders(player)).isEmpty();
    }
}
