package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.17 - thong ke doanh thu cua cac san.
 *
 * <p>Doanh thu gop = tong tien chu san da xac nhan thu truc tiep tu nguoi choi, tinh theo ngay
 * xac nhan (gio Viet Nam). Chua tru hoa hong (2.3.18) va tien da hoan (2.3.19).
 *
 * @param changePercent % thay doi so voi ky truoc co do dai bang nhau; null khi ky truoc bang 0
 */
public record RevenueStatsResponse(LocalDate from, LocalDate to, BigDecimal totalRevenue, long bookingCount,
                                   BigDecimal averagePerBooking, Previous previous, BigDecimal changePercent,
                                   List<DayPoint> daily, List<VenueRevenue> byVenue) {

    public record Previous(LocalDate from, LocalDate to, BigDecimal totalRevenue, long bookingCount) {
    }

    /** Du moi ngay trong ky, ke ca ngay khong co doanh thu. */
    public record DayPoint(LocalDate date, BigDecimal revenue, long bookings) {
    }

    public record VenueRevenue(UUID venueId, String venueName, BigDecimal revenue, long bookings) {
    }
}
