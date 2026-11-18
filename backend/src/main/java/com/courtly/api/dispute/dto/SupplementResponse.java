package com.courtly.api.dispute.dto;

import java.time.Instant;
import java.util.UUID;

/** Mot lan bo sung vua gui (2.3.91). Anh dinh kem tai len sau, qua POST .../evidences?supplementId=id. */
public record SupplementResponse(UUID id, String content, Instant createdAt, int supplementsRemaining, int evidenceRemaining) {
}
