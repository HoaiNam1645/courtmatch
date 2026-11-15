package com.courtly.api.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.42 - doanh thu phi nen tang (hoa hong Courtly thu tu chu san).
 *
 * <p>Doanh thu phi chi tinh hoa hong da ghi nhan (pending, due, adjusted, settled); hoa hong da thu la
 * settled, con phai thu la pending + due + adjusted. Hoa hong tam hoan do tranh chap (suspended) va
 * hoa hong duoc mien (waived) khong tinh vao doanh thu nen hien rieng.
 */
public record AdminFeeRevenueResponse(Summary summary, Trend trend, List<VenueRow> venues, List<VenueOption> venueOptions) {

    /**
     * @param bookingCount  so booking co hoa hong ghi nhan trong khoang
     * @param grossAmount   doanh thu gop cua cac booking do (chu san thu truc tiep tu nguoi choi)
     * @param effectiveRate ty le phi thuc te = ghi nhan / doanh thu gop (phan tram); null khi chua co doanh thu gop
     */
    public record Summary(long bookingCount, BigDecimal grossAmount, BigDecimal recorded, BigDecimal collected,
                          BigDecimal outstanding, BigDecimal effectiveRate, long suspendedCount, BigDecimal suspended,
                          long waivedCount, BigDecimal waived) {
    }

    /** @param period ngay (granularity day) hoac ngay dau thang (granularity month), gio Viet Nam */
    public record Point(LocalDate period, long bookingCount, BigDecimal grossAmount, BigDecimal recorded,
                        BigDecimal collected, BigDecimal outstanding) {
    }

    public record Trend(LocalDate from, LocalDate to, String granularity, List<Point> points) {
    }

    public record VenueRow(UUID venueId, String venueName, String ownerName, long bookingCount, BigDecimal grossAmount,
                           BigDecimal recorded, BigDecimal collected, BigDecimal outstanding) {
    }

    public record VenueOption(UUID id, String name) {
    }
}
