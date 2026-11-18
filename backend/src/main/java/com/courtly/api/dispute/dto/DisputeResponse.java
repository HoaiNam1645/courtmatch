package com.courtly.api.dispute.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Bao cao ve don dat san (2.3.85).
 *
 * @param status                awaiting_owner_response ngay sau khi tao
 * @param ownerResponseDeadline han chu san giai trinh
 */
public record DisputeResponse(UUID id, UUID bookingId, String bookingCode, String category, String categoryLabel,
                              String description, String status, Instant ownerResponseDeadline, Instant createdAt) {
}
