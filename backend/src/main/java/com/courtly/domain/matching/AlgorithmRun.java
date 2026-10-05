package com.courtly.domain.matching;

import com.courtly.common.enums.AlgorithmType;
import com.courtly.common.enums.RunStatus;
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

/** Metadata cua moi lan chay K-Means / DBSCAN / ghep cap (2.2.16, 2.3.73). */
@Entity
@Table(name = "algorithm_runs")
@Getter
@Setter
@NoArgsConstructor
public class AlgorithmRun implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "algorithm_type", nullable = false, length = 50)
    private AlgorithmType algorithmType;

    @Column(name = "algorithm_version", length = 50)
    private String algorithmVersion;

    /** Vi du: {"k": 4, "max_iter": 300, "random_state": 42}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters_json")
    private Map<String, Object> parametersJson;

    /** Vi du: {"silhouette": 0.62, "davies_bouldin": 0.81, "n_clusters": 4}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_json")
    private Map<String, Object> metricsJson;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "status", nullable = false, length = 30)
    private RunStatus status = RunStatus.RUNNING;

    @PrePersist
    void applyDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (startedAt == null) {
            startedAt = Instant.now();
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
