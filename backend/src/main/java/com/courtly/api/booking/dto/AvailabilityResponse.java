package com.courtly.api.booking.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Lich trong cua mot san trong mot ngay (2.1.26).
 *
 * @param closed         venue nghi ngay hom do, khi do danh sach san con rong
 * @param durationMinutes thoi luong dung de tinh moi khung gio
 */
public record AvailabilityResponse(UUID venueId,
                                   LocalDate date,
                                   boolean closed,
                                   @JsonFormat(pattern = "HH:mm") LocalTime openTime,
                                   @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
                                   int durationMinutes,
                                   List<CourtAvailability> courts) {

    public record CourtAvailability(UUID courtId,
                                    String courtName,
                                    String courtCode,
                                    List<Slot> slots,
                                    int availableCount) {
    }

    /**
     * @param totalPrice tien cho tron thoi luong, null khi khung gio khong dat duoc
     *                   hoac khong co bang gia cho doan gio do
     */
    public record Slot(@JsonFormat(pattern = "HH:mm") LocalTime startTime,
                       @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                       Instant startAt,
                       Instant endAt,
                       SlotStatus status,
                       BigDecimal totalPrice) {
    }
}
