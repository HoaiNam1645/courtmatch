package com.courtly.api.owner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.8 - lich san cua chu san theo ngay hoac tuan.
 *
 * @param stepMinutes do dai moi o lich
 */
public record VenueScheduleResponse(UUID venueId, LocalDate startDate, int stepMinutes, List<Day> days) {

    public record Day(LocalDate date, int dayOfWeek, boolean closed,
                      @JsonFormat(pattern = "HH:mm") LocalTime openTime,
                      @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
                      List<CourtSchedule> courts) {
    }

    public record CourtSchedule(UUID courtId, String courtName, String courtCode, String courtStatus,
                                List<Slot> slots) {
    }

    /**
     * @param status  available, pending, confirmed, blocked, past, unavailable (san con bao tri/ngung)
     * @param price   gia o con trong; null neu chua co bang gia
     * @param booking booking dang giu o nay (pending/confirmed), neu co
     * @param block   khoang khoa dang phu o nay, neu co
     */
    public record Slot(@JsonFormat(pattern = "HH:mm") LocalTime startTime,
                       @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                       Instant startAt, Instant endAt, String status, BigDecimal price,
                       BookingRef booking, BlockRef block) {
    }

    /** @param status trang thai cua don (pending_owner_confirmation, awaiting_payment, confirmed) */
    public record BookingRef(UUID bookingId, String bookingCode, String status, String playerName,
                             String playerPhone, Instant startAt, Instant endAt) {
    }

    public record BlockRef(UUID blockId, String reason, Instant startAt, Instant endAt) {
    }
}
