package com.courtly.domain.matching;

import com.courtly.common.BaseEntity;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ket qua phan cum cua mot nguoi choi trong mot lan chay thuat toan (2.2.18). */
@Entity
@Table(name = "player_clusters", uniqueConstraints =
        @UniqueConstraint(name = "pc_run_user_unique", columnNames = {"run_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PlayerCluster extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private AlgorithmRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Vi du: cluster_0, cluster_1, hoac noise voi DBSCAN. */
    @Column(name = "cluster_label", nullable = false, length = 50)
    private String clusterLabel;

    @Column(name = "distance_to_centroid", precision = 10, scale = 4)
    private BigDecimal distanceToCentroid;
}
