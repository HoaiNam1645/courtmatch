package com.courtly.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tao hoac sua voucher (B4). Ma khong doi duoc sau khi tao; de trong cac dieu kien nghia la khong yeu cau.
 *
 * @param discountPercent    1 den 100
 * @param maxDiscountAmount  tran giam toi da moi booking (VND), null = khong gioi han
 * @param totalLimit         tong so luot phat hanh, null = khong gioi han
 */
public record VoucherRequest(
        @NotBlank(message = "Vui long nhap ma voucher") @Size(max = 40, message = "Ma voucher toi da 40 ky tu") String code,
        @NotBlank(message = "Vui long nhap ten voucher") @Size(min = 2, max = 150, message = "Ten voucher tu 2 den 150 ky tu") String name,
        @Size(max = 1000, message = "Mo ta toi da 1000 ky tu") String description,
        @NotNull(message = "Vui long nhap phan tram giam") BigDecimal discountPercent,
        BigDecimal maxDiscountAmount,
        @NotNull(message = "Vui long chon ngay bat dau") Instant validFrom,
        @NotNull(message = "Vui long chon ngay het han") Instant validUntil,
        Integer totalLimit,
        BigDecimal minElo,
        Integer minBookingMatches,
        Integer minActiveWeeks) {
}
