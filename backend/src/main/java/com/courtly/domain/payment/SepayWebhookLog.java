package com.courtly.domain.payment;

import com.courtly.common.enums.WebhookProcessStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Luu nguyen payload webhook tu SePay de doi soat va debug (2.1.37).
 *
 * <p>{@code transactionCode} la unique nen webhook gui lai nhieu lan chi duoc xu ly mot lan.
 * Bang nay khong co cot created_at/id chuan nen khong ke thua BaseEntity.
 */
@Entity
@Table(name = "sepay_webhook_logs")
@Getter
@Setter
@NoArgsConstructor
public class SepayWebhookLog implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** NULL khi chua doi chieu duoc voi payment nao. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "transaction_code", nullable = false, unique = true, length = 255)
    private String transactionCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false)
    private Map<String, Object> payloadJson;

    @Column(name = "processed_status", nullable = false, length = 30)
    private WebhookProcessStatus processedStatus = WebhookProcessStatus.RECEIVED;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }

    @Transient
    private boolean newEntity = true;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.newEntity = false;
    }
}
