package com.courtly.api.owner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.10 - khoa mot khoang gio tren mot hoac nhieu san con cua cung mot san.
 *
 * @param startTime thoi diem bat dau (ISO-8601 UTC)
 * @param endTime   thoi diem ket thuc, phai sau startTime
 */
public record CreateCourtBlockRequest(

        @NotEmpty(message = "Vui long chon it nhat mot san con")
        @Size(max = 30, message = "Toi da 30 san con")
        List<@NotNull(message = "Ma san con khong hop le") UUID> courtIds,

        @NotNull(message = "Vui long nhap thoi diem bat dau")
        Instant startTime,

        @NotNull(message = "Vui long nhap thoi diem ket thuc")
        Instant endTime,

        @NotBlank(message = "Vui long nhap ly do khoa")
        @Size(min = 3, max = 255, message = "Ly do tu 3 den 255 ky tu")
        String reason) {

    public CreateCourtBlockRequest {
        reason = reason == null ? null : reason.trim();
    }
}
