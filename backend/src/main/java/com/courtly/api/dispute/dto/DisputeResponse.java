package com.courtly.api.dispute.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bao cao ve don dat san (2.3.85).
 *
 * @param status                awaiting_owner_response ngay sau khi tao
 * @param refundRequested       nguoi choi danh dau yeu cau hoan tien (2.3.87); chi ghi nhan, chua tao ban ghi hoan tien
 * @param refundAmount          so tien yeu cau hoan; null khi khong yeu cau hoan tien
 * @param ownerResponseDeadline han chu san giai trinh
 */
public record DisputeResponse(UUID id, UUID bookingId, String bookingCode, String category, String categoryLabel,
                              String description, String status, boolean refundRequested, BigDecimal refundAmount,
                              Instant ownerResponseDeadline, Instant createdAt, List<DisputeEvidenceResponse> evidences) {
}
