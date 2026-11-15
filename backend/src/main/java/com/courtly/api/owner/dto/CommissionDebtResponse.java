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
                                     List<VenueDebt> byVenue, Restriction restriction) {

    /**
     * Han che do cong no qua han (B2).
     *
     * @param level       0 = khong han che, 1 = chan nhan booking moi, 2 = tam an san
     * @param byAmount    muc hien tai ap dung vi dat nguong so tien qua han
     * @param byDays      muc hien tai ap dung vi dat nguong so ngay qua han
     * @param overdueDays so ngay ke tu han thanh toan cu nhat dang qua han
     * @param levels      nguong dang cau hinh de chu san biet ngay nao se bi han che
     */
    public record Restriction(int level, boolean byAmount, boolean byDays, BigDecimal overdueAmount, long overdueDays,
                              List<Level> levels) {
    }

    /** @param minOverdueAmount null neu khong dung nguong so tien; minOverdueDays null neu khong dung nguong so ngay */
    public record Level(int level, boolean enabled, BigDecimal minOverdueAmount, Integer minOverdueDays) {
    }

    public record VenueDebt(UUID venueId, String venueName, BigDecimal outstanding, BigDecimal overdue, long bookings) {
    }
}
