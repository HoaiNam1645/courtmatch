package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.18 - phi nen tang (hoa hong Courtly) cua tung booking.
 *
 * <p>Phi duoc chup lai (snapshot) theo chinh sach dang hieu luc luc ghi nhan, nen doi chinh sach
 * sau do khong anh huong booking cu.
 *
 * @param totalCommission tong hoa hong cua tap da loc (khong phu thuoc trang)
 * @param activePolicy    chinh sach se ap dung cho booking ghi nhan tu bay gio
 */
public record CommissionListResponse(List<Item> content, int page, int size, long totalElements, int totalPages,
                                     BigDecimal totalCommission, Policy activePolicy) {

    /** @param feeType percentage hoac fixed; feeValue la % hoac so tien co dinh */
    public record Policy(String name, String feeType, BigDecimal feeValue) {
    }

    /**
     * @param status  pending, due, suspended, adjusted, waived, settled
     * @param overdue true khi chua doi soat va da qua han
     */
    public record Item(UUID bookingId, String bookingCode, String bookingStatus,
                       UUID venueId, String venueName, String playerName,
                       BigDecimal grossAmount, String feeType, BigDecimal feeValue, BigDecimal commissionAmount,
                       String policyName, String status, boolean overdue,
                       Instant calculatedAt, Instant dueAt, Instant settledAt) {
    }
}
