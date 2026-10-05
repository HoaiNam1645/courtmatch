package com.courtly.domain.venue;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteVenueId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FavoriteVenueId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(venueId, that.venueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, venueId);
    }
}
