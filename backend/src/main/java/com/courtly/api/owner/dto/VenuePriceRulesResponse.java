package com.courtly.api.owner.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** 2.3.5 - bang gia cua tung san con trong mot dia diem. */
public record VenuePriceRulesResponse(UUID venueId, List<CourtPrices> courts) {

    /** @param courtStatus active, maintenance, inactive */
    public record CourtPrices(UUID courtId, String courtName, String courtCode, String courtStatus,
                              List<PriceRuleItem> rules) {
    }

    /**
     * @param dayOfWeek null = moi ngay
     * @param status    active hoac inactive (da ngung ap dung)
     */
    public record PriceRuleItem(UUID id,
                                Short dayOfWeek,
                                @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                BigDecimal pricePerHour,
                                LocalDate effectiveFrom,
                                LocalDate effectiveTo,
                                String status) {
    }
}
