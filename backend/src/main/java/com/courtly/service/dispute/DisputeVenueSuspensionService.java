package com.courtly.service.dispute;

import com.courtly.service.audit.AuditService;
import com.courtly.service.notification.DisputeNotifier;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 2.3.104 - tu dong tam ngung san bi xu thua nhieu lan.
 *
 * <p>"Xu thua" = bao cao ket luan hoan tien toan phan, mot nua hoac chu san khong phan hoi; khong tinh bac bo hay huy. San dang
 * active ma co tu {@code threshold} lan xu thua trong {@code windowDays} ngay gan nhat (cac lan cu van duoc tinh; sau khi admin bat lai thi chi
 * tam ngung lai khi co them it nhat mot lan xu thua moi) thi chuyen sang inactive va ghi ly do. Cac don da
 * dat khong bi anh huong. Goi ben trong transaction cua ket luan bao cao.
 */
@Service
public class DisputeVenueSuspensionService {

    private final JdbcTemplate jdbc;
    private final AuditService auditService;
    private final DisputeNotifier notifier;
    private final int threshold;
    private final int windowDays;

    public DisputeVenueSuspensionService(JdbcTemplate jdbc, AuditService auditService, DisputeNotifier notifier,
                                         @Value("${courtly.disputes.suspend-threshold:3}") int threshold,
                                         @Value("${courtly.disputes.suspend-window-days:90}") int windowDays) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.notifier = notifier;
        this.threshold = threshold;
        this.windowDays = windowDays;
    }

    public int threshold() {
        return threshold;
    }

    public int windowDays() {
        return windowDays;
    }

    /** @return true neu san vua bi tam ngung */
    public boolean evaluate(UUID venueId) {
        List<Map<String, Object>> venues = jdbc.queryForList("SELECT name, owner_id, status, suspended_at FROM venues WHERE id = ? FOR UPDATE", venueId);
        if (venues.isEmpty() || !"active".equals(venues.getFirst().get("status"))) {
            return false;
        }
        Instant now = Instant.now();
        Instant since = now.minus(Duration.ofDays(windowDays));
        java.sql.Timestamp suspendedAt = (java.sql.Timestamp) venues.getFirst().get("suspended_at");
        List<UUID> lost = jdbc.queryForList("""
                SELECT d.id FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id
                WHERE b.venue_id = ? AND d.status = 'resolved' AND d.resolution IN ('refund_full', 'refund_half', 'owner_no_response')
                  AND d.handled_at > ? ORDER BY d.handled_at
                """, UUID.class, venueId, java.sql.Timestamp.from(since));
        if (lost.size() < threshold) {
            return false;
        }
        // Da tung bi tam ngung va duoc bat lai: phai co them it nhat mot lan xu thua MOI sau lan tam ngung truoc moi tam ngung lai.
        if (suspendedAt != null) {
            Integer newer = jdbc.queryForObject("SELECT count(*) FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id "
                    + "WHERE b.venue_id = ? AND d.status = 'resolved' AND d.resolution IN ('refund_full', 'refund_half', 'owner_no_response') "
                    + "AND d.handled_at > ?", Integer.class, venueId, suspendedAt);
            if (newer == null || newer == 0) {
                return false;
            }
        }
        String name = (String) venues.getFirst().get("name");
        String reason = "Bị xử thua %d báo cáo đơn đặt sân trong %d ngày gần nhất (ngưỡng %d lần).".formatted(lost.size(), windowDays, threshold);
        jdbc.update("UPDATE venues SET status = 'inactive', suspension_reason = ?, suspended_at = now() WHERE id = ?", reason, venueId);
        auditService.record(null, "venue.auto_suspend", "venues", venueId, Map.of("status", "active"),
                Map.of("status", "inactive", "reason", reason, "venueName", name, "disputeIds", lost.stream().map(UUID::toString).toList()));
        notifier.venueSuspended(venueId, name, (UUID) venues.getFirst().get("owner_id"), reason);
        return true;
    }

    /** Admin bat lai san da bi tam ngung (ghi chu bat buoc); marker suspended_at giu lai de khong tinh lai cac lan xu thua cu. */
    public boolean reactivate(UUID venueId, com.courtly.domain.account.User admin, String note) {
        List<Map<String, Object>> venues = jdbc.queryForList(
                "SELECT name, owner_id, status, suspended_at FROM venues WHERE id = ? FOR UPDATE", venueId);
        if (venues.isEmpty() || !"inactive".equals(venues.getFirst().get("status")) || venues.getFirst().get("suspended_at") == null) {
            return false;
        }
        jdbc.update("UPDATE venues SET status = 'active', suspension_reason = NULL WHERE id = ?", venueId);
        auditService.record(admin, "venue.reactivate", "venues", venueId, Map.of("status", "inactive"),
                Map.of("status", "active", "note", note, "venueName", venues.getFirst().get("name")));
        notifier.venueReactivated(venueId, (String) venues.getFirst().get("name"), (UUID) venues.getFirst().get("owner_id"), note);
        return true;
    }
}
