package com.courtly.api.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.44 - chi tiet doi soat cua mot booking: booking, hoa hong da chot, thanh toan truc tiep, hoan tien,
 * tranh chap va lich su dieu chinh.
 *
 * @param unconfirmedPayment booking da hoan thanh nhung chu san chua xac nhan thu tien (B1)
 */
public record AdminCommissionDetailResponse(Commission commission, BookingInfo booking, Owner owner, Payment payment,
                                            boolean unconfirmedPayment, Settlement settlement, List<Refund> refunds,
                                            List<Dispute> disputes, List<HistoryEntry> history) {

    /**
     * @param status          pending, due, adjusted, suspended, waived, settled
     * @param voucherDiscount phan giam voucher Courtly chiu, da tru vao commissionAmount
     * @param courtlyOwed     phan Courtly con no chu san khi voucher vuot hoa hong
     * @param baseCommission  hoa hong day du tren gia goc = commissionAmount + voucherDiscount - courtlyOwed
     */
    public record Commission(UUID id, String status, String feeType, BigDecimal feeValue, String feePolicyName,
                             BigDecimal grossAmount, BigDecimal baseCommission, BigDecimal voucherDiscount,
                             BigDecimal commissionAmount, BigDecimal courtlyOwed, Instant courtlyOwedClearedAt,
                             Instant calculatedAt, Instant dueAt, boolean overdue, Instant settledAt) {
    }

    public record BookingInfo(UUID id, String bookingCode, String status, UUID venueId, String venueName,
                              String playerName, BigDecimal totalAmount, BigDecimal discountAmount, BigDecimal payableAmount,
                              String voucherCode, Instant startTime, Instant endTime, Instant completedAt) {
    }

    public record Owner(UUID id, String fullName, String email, String phone) {
    }

    /** Thanh toan truc tiep nguoi choi tra cho chu san (Courtly khong giu tien). */
    public record Payment(UUID id, String status, String method, BigDecimal amount, Instant payerReportedAt,
                          Instant confirmedAt, String rejectionReason) {
    }

    public record Settlement(UUID id, LocalDate periodFrom, LocalDate periodTo, String status, BigDecimal amountDue,
                             BigDecimal amountPaid) {
    }

    public record Refund(UUID id, BigDecimal amount, String status, String reason, Instant createdAt) {
    }

    public record Dispute(UUID id, String category, String status, String resolution, Instant createdAt) {
    }

    public record HistoryEntry(UUID id, String action, String actorName, Object oldData, Object newData, Instant createdAt) {
    }
}
