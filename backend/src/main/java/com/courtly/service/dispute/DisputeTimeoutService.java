package com.courtly.service.dispute;

import com.courtly.domain.payment.BookingDispute;
import com.courtly.domain.payment.BookingDisputeRepository;
import com.courtly.service.notification.DisputeNotifier;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.107 - nhac chu san o gio thu 24 va tu xu thua khi qua han giai trinh. Moi bao cao xu ly doc lap de mot loi khong chan cac bao cao khac. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeTimeoutService {

    static final Duration REMIND_AFTER = Duration.ofHours(24);

    private final JdbcTemplate jdbc;
    private final DisputeAutoResolver resolver;
    private final BookingDisputeRepository disputeRepository;
    private final DisputeNotifier notifier;

    /** @return so bao cao da tu xu thua */
    public int resolveOverdue(Instant now) {
        List<UUID> ids = jdbc.queryForList("SELECT id FROM booking_disputes WHERE status = 'awaiting_owner_response' AND owner_response IS NULL AND owner_response_deadline <= ? ORDER BY owner_response_deadline",
                UUID.class, Timestamp.from(now));
        int done = 0;
        for (UUID id : ids) {
            try {
                if (resolver.resolve(id, now)) {
                    done++;
                }
            } catch (Exception exception) {
                log.error("Khong tu xu ly duoc bao cao {}", id, exception);
            }
        }
        return done;
    }

    /** Nhac mot lan o gio thu 24 ke tu luc gui bao cao (chi khi con han); owner_reminded_at chong gui trung khi job chay lai. */
    public int remind(Instant now) {
        List<UUID> ids = jdbc.queryForList("SELECT id FROM booking_disputes WHERE status = 'awaiting_owner_response' AND owner_response IS NULL AND owner_reminded_at IS NULL AND created_at <= ? AND owner_response_deadline > ? ORDER BY created_at",
                UUID.class, Timestamp.from(now.minus(REMIND_AFTER)), Timestamp.from(now));
        int sent = 0;
        for (UUID id : ids) {
            try {
                if (remindOne(id, now)) {
                    sent++;
                }
            } catch (Exception exception) {
                log.error("Khong gui duoc nhac giai trinh cho bao cao {}", id, exception);
            }
        }
        return sent;
    }

    @Transactional
    boolean remindOne(UUID id, Instant now) {
        int updated = jdbc.update("UPDATE booking_disputes SET owner_reminded_at = ? WHERE id = ? AND owner_reminded_at IS NULL AND status = 'awaiting_owner_response'",
                Timestamp.from(now), id);
        if (updated == 0) {
            return false;
        }
        BookingDispute dispute = disputeRepository.findWithParties(id).orElse(null);
        if (dispute == null) {
            return false;
        }
        notifier.ownerReminder(dispute);
        return true;
    }
}
