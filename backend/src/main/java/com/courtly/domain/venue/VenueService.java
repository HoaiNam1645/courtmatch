package com.courtly.domain.venue;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/** Bang trung gian venues N-N services, co them gia va ghi chu. */
@Entity
@Table(name = "venue_services")
@Getter
@Setter
@NoArgsConstructor
public class VenueService implements Persistable<VenueServiceId> {

    @EmbeddedId
    private VenueServiceId id = new VenueServiceId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("venueId")
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("serviceId")
    @JoinColumn(name = "service_id")
    private Service service;

    /** NULL = khong niem yet gia, 0 = mien phi. */
    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "note")
    private String note;

    public VenueService(Venue venue, Service service, BigDecimal price, String note) {
        this.venue = venue;
        this.service = service;
        this.price = price;
        this.note = note;
        this.id = new VenueServiceId(venue.getId(), service.getId());
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
