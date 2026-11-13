package com.courtly.api.owner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * 2.3.7 - gio mo cua cua mot ngay trong tuan.
 *
 * @param dayOfWeek 1 = Thu hai ... 7 = Chu nhat
 * @param closed    true = nghi ca ngay; khi do bo qua gio mo / dong
 */
public record OperatingHoursInput(

        @NotNull(message = "Thieu ngay trong tuan")
        @Min(value = 1, message = "Ngay trong tuan tu 1 den 7")
        @Max(value = 7, message = "Ngay trong tuan tu 1 den 7")
        Integer dayOfWeek,

        boolean closed,

        @JsonFormat(pattern = "HH:mm")
        LocalTime openTime,

        @JsonFormat(pattern = "HH:mm")
        LocalTime closeTime) {
}
