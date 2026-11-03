package com.courtly.api.partner.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/** Dieu kien tim doi tac cho mot buoi choi cu the (2.2.20 - 2.2.23). */
public record PartnerSearchQuery(

        @NotNull(message = "Vui long chon khu vuc choi")
        UUID preferredLocationId,

        @NotNull(message = "Vui long chon ngay gio choi")
        @Future(message = "Thoi gian choi phai o tuong lai")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant scheduledAt,

        @NotNull(message = "Vui long chon thoi luong")
        @Min(value = 30, message = "Thoi luong toi thieu 30 phut")
        @Max(value = 240, message = "Thoi luong toi da 240 phut")
        Integer durationMinutes,

        @Min(value = 50, message = "Do lech Elo tu 50 den 500")
        @Max(value = 500, message = "Do lech Elo tu 50 den 500")
        Integer maxRatingDifference,

        @Min(value = 0, message = "So trang bat dau tu 0")
        Integer page,

        @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
        @Max(value = 100, message = "Kich thuoc trang tu 1 den 100")
        Integer size) {

    public int ratingDifference() {
        return maxRatingDifference == null ? 200 : maxRatingDifference;
    }

    public int pageNumber() {
        return page == null ? 0 : page;
    }

    public int pageSize() {
        return size == null ? 20 : size;
    }
}
