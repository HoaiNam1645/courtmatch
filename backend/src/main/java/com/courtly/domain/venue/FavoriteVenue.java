package com.courtly.domain.venue;

import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/** San yeu thich cua nguoi choi. */
@Entity
@Table(name = "favorite_venues")
@Getter
@Setter
@NoArgsConstructor
public class FavoriteVenue implements Persistable<FavoriteVenueId> {

    @EmbeddedId
    private FavoriteVenueId id = new FavoriteVenueId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("venueId")
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public FavoriteVenue(User user, Venue venue) {
        this.user = user;
        this.venue = venue;
        this.id = new FavoriteVenueId(user.getId(), venue.getId());
    }

    @PrePersist
    void applyDefaults() {
        if (createdAt == null) {
            createdAt = Instant.now();
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
