package com.courtly.api.voucher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Xem truoc muc giam cua voucher cho mot lua chon dat san (B4); khong ghi gi. */
public record VoucherPreviewRequest(
        @NotBlank(message = "Vui long nhap ma voucher") @Size(max = 40, message = "Ma voucher toi da 40 ky tu") String voucherCode,
        @NotNull(message = "Thieu san con") UUID courtId,
        @NotNull(message = "Thieu gio bat dau") Instant startTime,
        @NotNull(message = "Thieu thoi luong") Integer durationMinutes) {
}
