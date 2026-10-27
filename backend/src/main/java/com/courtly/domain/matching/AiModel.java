package com.courtly.domain.matching;

import com.courtly.common.enums.ModelStatus;
import com.courtly.common.enums.ModelType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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

/** Metadata cua model AI/baseline dung cho xep hang doi tac (2.2.16). */
@Entity
@Table(name = "ai_models")
@Getter
@Setter
@NoArgsConstructor
public class AiModel implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "model_type", nullable = false, length = 50)
    private ModelType modelType;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "artifact_path", length = 500)
    private String artifactPath;

    @Column(name = "artifact_checksum", length = 128)
    private String artifactChecksum;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_schema_json")
    private Map<String, Object> featureSchemaJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_json")
    private Map<String, Object> parametersJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_json")
    private Map<String, Object> metricsJson;

    @Column(name = "training_started_at", nullable = false)
    private Instant trainingStartedAt;

    @Column(name = "trained_at")
    private Instant trainedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "status", nullable = false, length = 30)
    private ModelStatus status = ModelStatus.TRAINING;

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (trainingStartedAt == null) {
            trainingStartedAt = Instant.now();
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
