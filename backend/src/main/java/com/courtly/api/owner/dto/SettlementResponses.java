package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 2.3.23 - cac ky doi soat hoa hong giua chu san va Courtly. */
public final class SettlementResponses {

    private SettlementResponses() {
    }

    /**
     * @param status          open, pending_review, partial, paid, overdue, disputed
     * @param remainingAmount con phai tra = amountDue - amountPaid (khong am)
     * @param bookingCount    so booking thuoc ky
     */
    public record Summary(UUID id, LocalDate periodFrom, LocalDate periodTo, BigDecimal openingOutstanding,
                          BigDecimal commissionTotal, BigDecimal adjustmentTotal, BigDecimal amountDue,
                          BigDecimal amountPaid, BigDecimal remainingAmount, String status, long bookingCount,
                          Instant ownerSubmittedAt, Instant reviewedAt, Instant paidAt, String note, Instant createdAt) {
    }

    /** @param statusCounts du moi trang thai (0 neu chua co) de giao dien hien du cac tab */
    public record ListResponse(List<Summary> content, int page, int size, long totalElements, int totalPages,
                               Map<String, Long> statusCounts) {
    }

    /** @param bookings danh sach booking cua ky, phan trang theo page/size cua yeu cau */
    public record Detail(Summary settlement, List<CommissionListResponse.Item> bookings, int page, int size,
                         long totalBookings, int totalPages) {
    }
}
