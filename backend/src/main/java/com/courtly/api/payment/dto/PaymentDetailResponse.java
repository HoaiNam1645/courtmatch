package com.courtly.api.payment.dto;

import com.courtly.common.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Man hinh thanh toan (2.1.34 - 2.1.36, 2.1.39, 2.1.41, 2.1.42).
 *
 * <p>{@code bankName} va {@code accountName} khong nam trong bang payments: chung la cau hinh
 * cua nen tang, lay tu {@code courtly.payment.sepay}. Doi tai khoan nhan tien thi cac don cu
 * van giu nguyen so tai khoan da hien thi luc tao.
 *
 * <p>{@code secondsRemaining} do backend tinh, khong de frontend tu tru dong ho may minh -
 * dong ho lech lam nguoi dung thay het han som hoac muon hon thuc te.
 */
public record PaymentDetailResponse(UUID id,
                                    UUID bookingId,
                                    String bookingCode,
                                    String provider,
                                    BigDecimal amount,
                                    PaymentStatus status,
                                    String qrUrl,
                                    String bankName,
                                    String bankAccountNo,
                                    String bankAccountName,
                                    String transferContent,
                                    String providerTransactionId,
                                    Instant paidAt,
                                    Instant expiredAt,
                                    long secondsRemaining,
                                    Instant createdAt,
                                    Instant updatedAt) {
}
