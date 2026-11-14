package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.27 - chi tiet xac nhan thanh toan truc tiep cua mot booking.
 *
 * @param canConfirm true khi nguoi choi da bao thanh toan va chua qua han: chu san xac nhan duoc da thu tien
 * @param canReject  true khi nguoi choi da bao thanh toan va dang cho chu san xu ly
 * @param expired    da qua han thanh toan; khong con xac nhan duoc, can xu ly thu cong
 */
public record OwnerPaymentDetailResponse(UUID paymentId, String status, String method, BigDecimal amount,
                                         String bankName, String bankAccountNo, String bankAccountName,
                                         String referenceContent, String proofUrl, Instant payerReportedAt,
                                         Instant confirmedAt, Instant rejectedAt, String rejectionReason,
                                         Instant expiredAt, boolean expired, boolean canConfirm, boolean canReject,
                                         Booking booking, List<Step> timeline) {

    public record Booking(UUID id, String bookingCode, String status, UUID venueId, String venueName,
                          String playerName, String playerPhone, BigDecimal totalAmount, String note,
                          List<BookingRequestResponse.CourtSlot> slots) {
    }

    public record Step(String oldStatus, String newStatus, String reason, String actorName, boolean bySystem,
                       Instant createdAt) {
    }
}
