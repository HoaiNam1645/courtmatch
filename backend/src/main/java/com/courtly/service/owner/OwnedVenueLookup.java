package com.courtly.service.owner;

import com.courtly.common.exception.ApiException;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Tim san / san con THUOC chu san dang dang nhap (2.3.4 tro di).
 *
 * <p>San cua chu san khac tra 404 thay vi 403 de khong tiet lo san do co ton tai.
 */
@Component
@RequiredArgsConstructor
public class OwnedVenueLookup {

    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;

    public Venue requireVenue(UUID ownerId, UUID venueId) {
        return venueRepository.findById(venueId)
                .filter(venue -> venue.getOwner().getId().equals(ownerId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay san nay."));
    }

    public Court requireCourt(UUID ownerId, UUID venueId, UUID courtId) {
        Venue venue = requireVenue(ownerId, venueId);
        return courtRepository.findById(courtId)
                .filter(court -> court.getVenue().getId().equals(venue.getId()))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay san con nay."));
    }
}
