package com.courtly.api.booking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Tam tinh tien cho mot lua chon dat san (2.1.27).
 *
 * @param durationMinutes giao dien cho chon 60, 90 hoac 120 phut
 */
public record QuoteRequest(

        @NotNull(message = "Thieu san con")
        UUID courtId,

        @NotNull(message = "Thieu gio bat dau")
        @Future(message = "Gio bat dau phai o tuong lai")
        Instant startTime,

        @NotNull(message = "Thieu thoi luong")
        @Min(value = 30, message = "Thoi luong toi thieu 30 phut")
        Integer durationMinutes) {
}
