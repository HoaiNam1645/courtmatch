package com.courtly.api.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Ket qua tam tinh (2.1.27).
 *
 * <p>So tien o day do server tinh tu court_price_rules. Khi tao don that, server tinh lai
 * mot lan nua chu khong tin so tien client gui len.
 *
 * @param available khung gio con dat duoc tai thoi diem bao gia
 * @param segments  chi tiet tung doan gia, de giao dien giai thich duoc cach ra so tien
 */
public record QuoteResponse(UUID venueId,
                            String venueName,
                            UUID courtId,
                            String courtName,
                            LocalDate date,
                            Instant startTime,
                            Instant endTime,
                            @JsonFormat(pattern = "HH:mm") LocalTime startTimeOfDay,
                            @JsonFormat(pattern = "HH:mm") LocalTime endTimeOfDay,
                            int durationMinutes,
                            BigDecimal totalAmount,
                            boolean available,
                            String unavailableReason,
                            List<PriceSegment> segments) {

    /** Mot doan thoi gian ap dung cung mot muc gia. */
    public record PriceSegment(@JsonFormat(pattern = "HH:mm") LocalTime startTime,
                               @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                               BigDecimal pricePerHour,
                               BigDecimal amount) {
    }
}
