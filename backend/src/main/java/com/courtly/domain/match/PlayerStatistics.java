package com.courtly.domain.match;

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
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/**
 * Bang tong hop doc nhanh cho ho so va thuat toan ghep cap (2.2.10).
 *
 * <p>Man hinh ho so doc thang tu bang nay, khong tinh lai tu matches moi lan mo.
 */
@Entity
@Table(name = "player_statistics")
@Getter
@Setter
@NoArgsConstructor
public class PlayerStatistics implements Persistable<UUID> {

    /** Diem rating khoi diem cho nguoi choi moi. */
    public static final BigDecimal DEFAULT_RATING = BigDecimal.valueOf(1000);

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "total_matches", nullable = false)
    private int totalMatches;

    @Column(name = "total_wins", nullable = false)
    private int totalWins;

    @Column(name = "total_losses", nullable = false)
    private int totalLosses;

    @Column(name = "win_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal winRate = BigDecimal.ZERO;

    @Column(name = "rating_score", nullable = false, precision = 8, scale = 2)
    private BigDecimal ratingScore = DEFAULT_RATING;

    @Column(name = "avg_points_scored", nullable = false, precision = 8, scale = 2)
    private BigDecimal avgPointsScored = BigDecimal.ZERO;

    @Column(name = "avg_points_conceded", nullable = false, precision = 8, scale = 2)
    private BigDecimal avgPointsConceded = BigDecimal.ZERO;

    @Column(name = "last_played_at")
    private Instant lastPlayedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PlayerStatistics(User user) {
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
