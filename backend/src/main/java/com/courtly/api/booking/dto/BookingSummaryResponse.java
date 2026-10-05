package com.courtly.api.booking.dto;

import com.courtly.common.enums.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mot dong trong lich su dat san (2.1.32). */
public record BookingSummaryResponse(UUID id,
                                     String bookingCode,
                                     BookingStatus status,
                                     BigDecimal totalAmount,
                                     UUID venueId,
                                     String venueSlug,
                                     String venueName,
                                     String venueAddress,
                                     String venueCoverImageUrl,
                                     String courtName,
                                     Instant startTime,
                                     Instant endTime,
                                     Instant expiresAt,
                                     Instant createdAt) {
}
