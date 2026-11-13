package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.19 - so tien thuc nhan cua tung booking: doanh thu gop - hoa hong Courtly - tien chu san
 * da hoan cho nguoi choi (da duoc xac nhan).
 *
 * @param totals tong cua toan bo tap da loc, khong phu thuoc trang
 */
public record NetIncomeResponse(LocalDate from, LocalDate to, List<Item> content, int page, int size,
                                long totalElements, int totalPages, Totals totals) {

    /**
     * @param commissionEstimated true = booking chua ghi nhan hoa hong, dang dung muc uoc tinh theo chinh sach hien hanh
     * @param commissionStatus    trang thai hoa hong da ghi nhan, null khi uoc tinh
     * @param pendingRefundAmount khoan hoan da duyet nhung chua duoc xac nhan - CHUA tru vao so thuc nhan
     */
    public record Item(UUID paymentId, UUID bookingId, String bookingCode, String bookingStatus,
                       UUID venueId, String venueName, String playerName, Instant confirmedAt,
                       BigDecimal grossAmount, BigDecimal commissionAmount, boolean commissionEstimated,
                       String commissionStatus, BigDecimal refundedAmount, BigDecimal pendingRefundAmount,
                       BigDecimal netAmount) {
    }

    public record Totals(BigDecimal grossAmount, BigDecimal commissionAmount, BigDecimal refundedAmount,
                         BigDecimal pendingRefundAmount, BigDecimal netAmount, long bookings) {
    }
}
