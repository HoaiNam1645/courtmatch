package com.courtly.api.review.dto;

import java.time.Instant;
import java.util.UUID;

/** Danh gia san cua chinh nguoi choi sau khi viet hoac sua (2.3.77, 2.3.78). */
public record MyVenueReviewResponse(UUID id, UUID venueId, UUID bookingId, int rating, String comment,
                                    String status, Instant createdAt, Instant updatedAt) {
}
