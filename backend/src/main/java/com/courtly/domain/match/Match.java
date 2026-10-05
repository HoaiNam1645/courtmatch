package com.courtly.domain.match;

import com.courtly.common.AuditedEntity;
import com.courtly.common.enums.MatchStatus;
import com.courtly.common.enums.MatchType;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.venue.Venue;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tran dau don, doi hoac doi nam nu (2.2.37). */
@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
public class Match extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @Column(name = "match_type", nullable = false, length = 30)
    private MatchType matchType;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "status", nullable = false, length = 30)
    private MatchStatus status = MatchStatus.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MatchPlayer> players = new ArrayList<>();

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("gameNo ASC")
    private List<MatchGame> games = new ArrayList<>();

    @OneToOne(mappedBy = "match", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private MatchResult result;
}
