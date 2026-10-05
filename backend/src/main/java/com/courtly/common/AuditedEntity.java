package com.courtly.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Bo sung cot updated_at cho cac bang co theo doi lan sua cuoi. */
@MappedSuperclass
@Getter
@Setter
public abstract class AuditedEntity extends BaseEntity {

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void initUpdatedAt() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    void refreshUpdatedAt() {
        updatedAt = Instant.now();
    }
}
