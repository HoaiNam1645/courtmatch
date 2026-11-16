package com.courtly.api.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** B4 - quan tri vien quan ly voucher giam gia (Courtly chiu phan giam). */
public final class AdminVoucherResponses {

    private AdminVoucherResponses() {
    }

    /**
     * @param status      trang thai luu: active hoac locked
     * @param state       trang thai hien tai: active, locked, scheduled (chua bat dau), expired, exhausted (het luot)
     * @param usedCount   so luot dang dung (khong tinh luot da tra lai)
     * @param remaining   so luot con lai; null neu khong gioi han
     * @param totalDiscount tong tien giam Courtly da chiu cho cac luot dang dung
     */
    public record Summary(UUID id, String code, String name, BigDecimal discountPercent, BigDecimal maxDiscountAmount,
                          Instant validFrom, Instant validUntil, Integer totalLimit, long usedCount, Integer remaining,
                          BigDecimal totalDiscount, BigDecimal minElo, Integer minBookingMatches, Integer minActiveWeeks,
                          String status, String state, Instant createdAt) {
    }

    /** @param voucherCount so voucher; redemptions va totalDiscount la tong tren moi voucher (luot dang dung) */
    public record Totals(long voucherCount, long redemptions, BigDecimal totalDiscount) {
    }

    public record ListResponse(List<Summary> content, Totals totals) {
    }

    /** @param status applied (dang dung) hoac released (da tra lai) */
    public record Redemption(UUID id, String userName, String bookingCode, BigDecimal originalAmount, BigDecimal discountAmount,
                             String status, Instant createdAt, Instant releasedAt) {
    }

    public record Detail(Summary summary, String description, String createdBy, List<Redemption> redemptions) {
    }
}
