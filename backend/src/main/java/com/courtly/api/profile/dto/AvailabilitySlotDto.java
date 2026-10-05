package com.courtly.api.profile.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * Mot khung gio ranh trong tuan (2.1.10).
 *
 * @param dayOfWeek 1 = Thu hai ... 7 = Chu nhat
 */
public record AvailabilitySlotDto(

        @NotNull(message = "Thieu thu trong tuan")
        @Min(value = 1, message = "Thu trong tuan tu 1 den 7")
        @Max(value = 7, message = "Thu trong tuan tu 1 den 7")
        Short dayOfWeek,

        @NotNull(message = "Thieu gio bat dau")
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @NotNull(message = "Thieu gio ket thuc")
        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime) {

    @JsonIgnore
    @AssertTrue(message = "Gio ket thuc phai sau gio bat dau")
    public boolean isTimeRangeValid() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}
