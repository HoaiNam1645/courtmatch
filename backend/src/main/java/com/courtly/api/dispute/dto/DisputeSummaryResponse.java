package com.courtly.api.dispute.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mot dong trong danh sach bao cao da gui (2.3.89).
 *
 * @param closed          resolved, rejected hoac cancelled
 * @param resolution      ket luan khi da dong (vi du refund_full, refund_half, rejected); null khi con xu ly
 * @param evidenceCount   so anh bang chung nguoi bao cao da gui
 */
public record DisputeSummaryResponse(UUID id, UUID bookingId, String bookingCode, String venueName, String category,
                                     String categoryLabel, String status, boolean closed, boolean refundRequested,
                                     BigDecimal refundAmount, int evidenceCount, Instant createdAt,
                                     Instant ownerResponseDeadline, String resolution) {
}
