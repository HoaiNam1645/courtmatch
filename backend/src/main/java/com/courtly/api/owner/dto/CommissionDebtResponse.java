package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.20 - tong cong no hoa hong cua chu san voi Courtly.
 *
 * <p>Cong no = tong hoa hong cua cac booking chua doi soat (pending, due, adjusted), tong hop
 * truc tiep tu hoa hong tung booking. Hoa hong dang tranh chap (suspended) duoc tam hoan nen
 * KHONG tinh vao cong no, va hoa hong duoc mien (waived) khong phai tra.
 *
 * @param totalOutstanding tong cong no hien tai = overdueAmount + upcomingAmount
 * @param overdueAmount    phan da qua han thanh toan
 * @param upcomingAmount   phan chua den han
 * @param suspendedAmount  hoa hong tam hoan do tranh chap, chua tinh vao cong no
 * @param settledAmount    tong hoa hong da doi soat xong tu truoc den nay
 * @param oldestOverdueAt  han thanh toan cu nhat dang qua han; null neu khong co
 * @param nextDueAt        han thanh toan gan nhat sap toi trong phan chua den han; null neu khong co
 */
public record CommissionDebtResponse(Instant asOf, BigDecimal totalOutstanding, long outstandingCount,
                                     BigDecimal overdueAmount, long overdueCount,
                                     BigDecimal upcomingAmount, long upcomingCount,
                                     BigDecimal suspendedAmount, long suspendedCount,
                                     BigDecimal settledAmount, Instant oldestOverdueAt, Instant nextDueAt,
                                     List<VenueDebt> byVenue) {

    public record VenueDebt(UUID venueId, String venueName, BigDecimal outstanding, BigDecimal overdue, long bookings) {
    }
}
