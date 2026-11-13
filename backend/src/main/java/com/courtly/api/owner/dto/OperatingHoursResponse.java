package com.courtly.api.owner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.7 - gio hoat dong ca tuan.
 *
 * @param conflicts booking chua choi nam ngoai gio hoat dong hien tai. Courtly KHONG tu huy
 *                  cac booking nay; chu san tu lien he nguoi choi hoac xu ly theo chinh sach.
 */
public record OperatingHoursResponse(List<DayHours> days, List<Conflict> conflicts) {

    /** @param configured false = ngay chua tung duoc khai bao (dang coi nhu nghi) */
    public record DayHours(int dayOfWeek, boolean closed,
                           @JsonFormat(pattern = "HH:mm") LocalTime openTime,
                           @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
                           boolean configured) {
    }

    public record Conflict(UUID bookingId, String bookingCode, String courtName,
                           Instant startTime, Instant endTime, String status) {
    }
}
