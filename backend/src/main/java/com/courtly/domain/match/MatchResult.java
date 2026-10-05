package com.courtly.domain.match;

import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/** Ket qua tong cua tran, dung chung khoa chinh voi {@link Match}. */
@Entity
@Table(name = "match_results")
@Getter
@Setter
@NoArgsConstructor
public class MatchResult implements Persistable<UUID> {

    @Id
    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "match_id")
    private Match match;

    /** 1 hoac 2. */
    @Column(name = "winning_team", nullable = false)
    private short winningTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "note")
    private String note;

    public MatchResult(Match match, short winningTeam) {
        this.match = match;
        this.matchId = match.getId();
        this.winningTeam = winningTeam;
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
        return matchId;
    }
}
