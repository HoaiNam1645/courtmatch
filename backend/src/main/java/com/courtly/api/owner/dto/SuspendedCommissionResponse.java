package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.97 - hoa hong dang tam hoan do tranh chap: chua tinh vao cong no den han va chua bi mien,
 * se tinh lai hoac dieu chinh khi bao cao co ket luan.
 *
 * @param totalAmount tong hoa hong dang hoan (cua toan bo danh sach)
 * @param disputeId   bao cao dang xu ly cua don (null neu khong con bao cao dang xu ly)
 */
public record SuspendedCommissionResponse(List<Item> items, BigDecimal totalAmount) {

    public record Item(UUID bookingId, String bookingCode, UUID venueId, String venueName, BigDecimal commissionAmount,
                       BigDecimal voucherDiscount, UUID disputeId, Instant suspendedAt) {
    }
}
