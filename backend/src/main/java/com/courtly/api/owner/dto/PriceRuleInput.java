package com.courtly.api.owner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 2.3.5 - mot quy tac gia cua san con.
 *
 * @param dayOfWeek     1 = Thu hai ... 7 = Chu nhat; null = ap dung moi ngay
 * @param effectiveFrom ngay bat dau ap dung; bo trong = hom nay
 * @param effectiveTo   ngay het ap dung (tinh ca ngay do); bo trong = khong thoi han
 */
public record PriceRuleInput(

        @Min(value = 1, message = "Thu trong tuan tu 1 (Thu hai) den 7 (Chu nhat)")
        @Max(value = 7, message = "Thu trong tuan tu 1 (Thu hai) den 7 (Chu nhat)")
        Integer dayOfWeek,

        @NotNull(message = "Vui long nhap gio bat dau")
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @NotNull(message = "Vui long nhap gio ket thuc")
        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,

        @NotNull(message = "Vui long nhap gia moi gio")
        @DecimalMin(value = "0", message = "Gia khong duoc am")
        @DecimalMax(value = "10000000", message = "Gia toi da 10.000.000d/gio")
        @Digits(integer = 8, fraction = 0, message = "Gia la so nguyen dong")
        BigDecimal pricePerHour,

        LocalDate effectiveFrom,

        LocalDate effectiveTo) {
}
