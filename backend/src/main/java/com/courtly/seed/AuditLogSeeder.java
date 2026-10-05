package com.courtly.seed;

import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.system.AuditLog;
import com.courtly.domain.system.AuditLogRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Buoc 10: audit_logs. */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class AuditLogSeeder implements Seeder {

    private static final String ADMIN_IP = "113.161.42.17";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)";

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    /** @param actorKey null khi hanh dong do he thong tu thuc hien */
    private record AuditSeed(String key, String actorKey, String action, String entityType,
                             UUID entityId, Map<String, Object> oldData, Map<String, Object> newData,
                             int daysAgo) {
    }

    @Override
    public String name() {
        return "audit logs";
    }

    @Override
    @Transactional
    public void seed() {
        if (auditLogRepository.count() > 0) {
            log.info("  [audit] da co du lieu, bo qua");
            return;
        }

        List<AuditSeed> entries = List.of(
                new AuditSeed("audit-01", "admin", "venue.approve", "venues",
                        SeedIds.of("venue:venue-01"),
                        Map.of("approval_status", "pending", "status", "pending"),
                        Map.of("approval_status", "approved", "status", "active"), 60),
                new AuditSeed("audit-02", "admin", "owner.verify", "court_owner_profiles",
                        SeedIds.of("user:owner-01"),
                        Map.of("verification_status", "pending"),
                        Map.of("verification_status", "verified"), 60),
                new AuditSeed("audit-03", "admin", "platform_fee.create", "platform_fee_configs",
                        SeedIds.of("platform-fee:default"),
                        null,
                        Map.of("fee_type", "percentage", "fee_value", 10, "status", "active"), 90),
                new AuditSeed("audit-04", "admin", "withdrawal.approve", "withdrawal_requests",
                        SeedIds.of("withdrawal:owner-01-paid"),
                        Map.of("status", "pending"),
                        Map.of("status", "paid", "amount", 189000), 0),
                new AuditSeed("audit-05", "admin", "report.resolve", "reports",
                        SeedIds.of("report:report-02"),
                        Map.of("status", "pending"),
                        Map.of("status", "resolved"), 28),
                new AuditSeed("audit-06", "owner-01", "court_block.create", "court_blocks",
                        SeedIds.of("court-block:venue-01-vip"),
                        null,
                        Map.of("reason", "Bao tri mat san dinh ky", "status", "active"), 1),
                new AuditSeed("audit-07", null, "booking.expire", "bookings",
                        SeedIds.of("booking:booking-05"),
                        Map.of("status", "pending_payment"),
                        Map.of("status", "expired"), 4));

        for (AuditSeed seed : entries) {
            AuditLog entry = new AuditLog();
            entry.setId(SeedIds.of("audit-log:" + seed.key()));
            entry.setActor(seed.actorKey() == null ? null : user(seed.actorKey()));
            entry.setAction(seed.action());
            entry.setEntityType(seed.entityType());
            entry.setEntityId(seed.entityId());
            entry.setOldData(seed.oldData());
            entry.setNewData(seed.newData());
            entry.setIpAddress(seed.actorKey() == null ? null : ADMIN_IP);
            entry.setUserAgent(seed.actorKey() == null ? null : USER_AGENT);
            entry.setCreatedAt(Instant.now().minus(seed.daysAgo(), ChronoUnit.DAYS));
            auditLogRepository.save(entry);
        }

        log.info("  [audit] {} ban ghi", entries.size());
    }

    private User user(String key) {
        return userRepository.findById(SeedIds.of("user:" + key))
                .orElseThrow(() -> new IllegalStateException("Thieu tai khoan '" + key + "'"));
    }
}
