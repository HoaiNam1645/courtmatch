package com.courtly.domain.system;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /** Lan thay doi gan nhat cua mot doi tuong theo hanh dong (2.3.28). */
    Optional<AuditLog> findFirstByEntityTypeAndEntityIdAndActionOrderByCreatedAtDesc(String entityType, UUID entityId, String action);
}
