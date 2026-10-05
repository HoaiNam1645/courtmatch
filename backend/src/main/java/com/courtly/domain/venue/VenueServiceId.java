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
public class VenueServiceId implements Serializable {

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VenueServiceId that)) {
            return false;
        }
        return Objects.equals(venueId, that.venueId) && Objects.equals(serviceId, that.serviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(venueId, serviceId);
    }
}
