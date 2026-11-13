package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 2.3.16 - thanh toan truc tiep cua nguoi choi gui toi cac san cua chu san.
 *
 * <p>Courtly khong giu tien san, nen khong co so du: chi la trang thai thanh toan va bang chung
 * do nguoi choi / chu san bao.
 *
 * @param statusCounts so thanh toan theo tung trang thai, khong phu thuoc bo loc trang thai
 */
public record OwnerPaymentListResponse(List<Item> content, int page, int size, long totalElements, int totalPages,
                                       Map<String, Long> statusCounts) {

    /**
     * @param status awaiting_payment, payer_reported, confirmed, rejected, expired, disputed, ...
     * @param method cash hoac owner_bank_transfer
     * @param proofUrl duong dan bang chung do nguoi choi gui, co the null
     */
    public record Item(UUID paymentId, UUID bookingId, String bookingCode, String bookingStatus,
                       UUID venueId, String venueName, String playerName, String playerPhone,
                       BigDecimal amount, String method, String status,
                       String bankName, String bankAccountNo, String bankAccountName, String referenceContent,
                       String proofUrl, Instant payerReportedAt, Instant confirmedAt,
                       Instant expiredAt, String rejectionReason, Instant createdAt) {
    }
}
