package com.courtly.api.booking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Tao yeu cau dat san (2.1.28).
 *
 * <p>Khong nhan so tien tu client. Server tu tinh lai tu court_price_rules.
 */
public record CreateBookingRequest(

        @NotNull(message = "Thieu san con")
        UUID courtId,

        @NotNull(message = "Thieu gio bat dau")
        @Future(message = "Gio bat dau phai o tuong lai")
        Instant startTime,

        @NotNull(message = "Thieu thoi luong")
        Integer durationMinutes,

        @Size(max = 500, message = "Ghi chu toi da 500 ky tu")
        String note) {

    public CreateBookingRequest {
        note = note == null || note.isBlank() ? null : note.trim();
    }
}
