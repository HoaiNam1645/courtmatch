package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 2.3.24 - mot lan chu san bao da thanh toan hoa hong cho Courtly.
 *
 * @param status      pending_review (cho Courtly xac nhan), confirmed, rejected
 * @param settlementId ky doi soat dang tra; null neu tra chung vao cong no
 */
public record CommissionPaymentResponse(UUID id, UUID settlementId, LocalDate periodFrom, LocalDate periodTo,
                                        BigDecimal amount, LocalDate paidOn, String referenceCode, String proofUrl,
                                        String note, String status, String reviewNote, Instant reviewedAt,
                                        Instant createdAt) {
}
