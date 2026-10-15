package com.courtly.api.payment.dto;

import com.courtly.common.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Snapshot of direct-payment instructions and the owner's confirmation state. */
public record PaymentDetailResponse(UUID id,
                                    UUID bookingId,
                                    String bookingCode,
                                    String method,
                                    BigDecimal amount,
                                    PaymentStatus status,
                                    String bankName,
                                    String bankAccountNo,
                                    String bankAccountName,
                                    String referenceContent,
                                    String proofUrl,
                                    Instant payerReportedAt,
                                    Instant confirmedAt,
                                    String rejectionReason,
                                    Instant expiredAt,
                                    long secondsRemaining,
                                    Instant createdAt,
                                    Instant updatedAt) {
}
