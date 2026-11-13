package com.courtly.api.owner.dto;

import java.time.Instant;
import java.util.UUID;

/** Mot khoang khoa san (2.3.10). */
public record CourtBlockItem(UUID id, UUID courtId, String courtName, String courtCode,
                             Instant startTime, Instant endTime, String reason, String status,
                             String createdByName, Instant createdAt) {
}
