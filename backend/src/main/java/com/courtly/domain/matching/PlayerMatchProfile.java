package com.courtly.domain.matching;

import com.courtly.common.enums.PlayingStyle;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Persistable;

/**
 * Vector dac trung phuc vu phan cum va ghep cap (2.2.5).
 *
 * <p>Tong hop tu player_profiles, player_statistics, player_availability_slots
 * va player_preferred_locations. Bang nay duoc tinh lai theo lo, khong cap nhat truc tiep tu UI.
 */
@Entity
@Table(name = "player_match_profiles")
@Getter
@Setter
@NoArgsConstructor
public class PlayerMatchProfile implements Persistable<UUID> {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "skill_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal skillScore = BigDecimal.ZERO;

    @Column(name = "win_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal winRate = BigDecimal.ZERO;

    @Column(name = "total_matches", nullable = false)
    private int totalMatches;

    /** Ghi truc tiep (khong co trigger nhu venues) vi bang khong luu latitude/longitude. */
    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "preferred_location")
    private Point preferredLocation;

    @Column(name = "preferred_radius_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal preferredRadiusKm = BigDecimal.valueOf(5);

    /** Vi du: {"1": [18,19,20], "6": [8,9,10]} - thu trong tuan -> cac gio thuong ranh. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "availability_score")
    private Map<String, List<Integer>> availabilityScore;

    @Column(name = "playing_style", length = 50)
    private PlayingStyle playingStyle;

    /** Ket qua phan cum moi nhat, copy tu player_clusters de doc nhanh. */
    @Column(name = "cluster_label", length = 50)
    private String clusterLabel;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PlayerMatchProfile(User user) {
        this.user = user;
        this.userId = user.getId();
    }

    @PrePersist
    void onInsert() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
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

    @Override
    public UUID getId() {
        return userId;
    }
}
