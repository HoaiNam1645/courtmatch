package com.courtly.job;

import com.courtly.service.notification.ReminderService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Nhac lich choi (2.3.59) va nhac xac nhan ket qua tran dau (2.3.60); moi moc chi gui mot lan nen chay lai khong gui trung. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "courtly.jobs.reminders", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReminderJob {

    private final ReminderService reminderService;

    @Scheduled(initialDelayString = "${courtly.jobs.reminders.initial-delay-ms:120000}",
            fixedDelayString = "${courtly.jobs.reminders.interval-ms:300000}")
    public void sendReminders() {
        try {
            Instant now = Instant.now();
            reminderService.remindBookings(now);
            reminderService.remindMatches(now);
        } catch (Exception exception) {
            // Mot lan loi khong duoc lam dung ca lich chay.
            log.error("Khong gui duoc nhac nho", exception);
        }
    }
}
