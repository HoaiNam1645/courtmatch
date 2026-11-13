package com.courtly.service.audit;

import com.courtly.domain.account.User;
import com.courtly.domain.system.AuditLog;
import com.courtly.domain.system.AuditLogRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Ghi nhat ky hanh dong cua chu san va quan tri vien vao audit_logs.
 *
 * <p>Goi ben trong transaction cua nghiep vu: hanh dong rollback thi nhat ky cung rollback,
 * khong co dong nhat ky cho mot thay doi chua tung xay ra.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void record(User actor, String action, String entityType, UUID entityId,
                       Map<String, Object> oldData, Map<String, Object> newData) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setActor(actor);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOldData(oldData);
        log.setNewData(newData);
        log.setCreatedAt(Instant.now());
        auditLogRepository.save(log);
    }
}
