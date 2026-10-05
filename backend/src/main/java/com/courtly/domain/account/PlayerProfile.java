package com.courtly.domain.account;

import com.courtly.common.enums.DominantHand;
import com.courtly.common.enums.Gender;
import com.courtly.common.enums.PlayType;
import com.courtly.common.enums.PlayingStyle;
import com.courtly.common.enums.SkillLevel;
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
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/** Ho so rieng cua nguoi choi, dung chung khoa chinh voi {@link User}. */
@Entity
@Table(name = "player_profiles")
@Getter
@Setter
@NoArgsConstructor
public class PlayerProfile implements Persistable<UUID> {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "skill_level", length = 30)
    private SkillLevel skillLevel;

    @Column(name = "skill_score", precision = 8, scale = 2)
    private BigDecimal skillScore;

    @Column(name = "dominant_hand", length = 20)
    private DominantHand dominantHand;

    @Column(name = "playing_style", length = 50)
    private PlayingStyle playingStyle;

    @Column(name = "preferred_play_type", length = 30)
    private PlayType preferredPlayType;

    @Column(name = "bio")
    private String bio;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PlayerProfile(User user) {
        this.user = user;
        this.userId = user.getId();
    }

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
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
