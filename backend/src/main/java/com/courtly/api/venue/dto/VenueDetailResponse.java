package com.courtly.api.venue.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Chi tiet mot san (2.1.18) - man hinh /san/:venueId. */
public record VenueDetailResponse(UUID id,
                                  String slug,
                                  String name,
                                  String description,
                                  String address,
                                  String ward,
                                  String district,
                                  String province,
                                  BigDecimal latitude,
                                  BigDecimal longitude,
                                  String phone,
                                  String email,
                                  BigDecimal averageRating,
                                  int reviewCount,
                                  BigDecimal minPricePerHour,
                                  BigDecimal maxPricePerHour,
                                  List<ImageResponse> images,
                                  List<ServiceResponse> services,
                                  List<OperatingHoursResponse> operatingHours,
                                  List<CourtResponse> courts) {

    /** Anh san, sap theo displayOrder; anh bia dung lam anh dai dien (2.1.24). */
    public record ImageResponse(UUID id, String imageUrl, String caption, int displayOrder, boolean cover) {
    }

    /** @param price null nghia la khong niem yet gia, 0 la mien phi */
    public record ServiceResponse(String code, String name, String description, BigDecimal price, String note) {
    }

    /** @param dayOfWeek 1 = Thu hai ... 7 = Chu nhat */
    public record OperatingHoursResponse(short dayOfWeek,
                                         @JsonFormat(pattern = "HH:mm") LocalTime openTime,
                                         @JsonFormat(pattern = "HH:mm") LocalTime closeTime,
                                         boolean closed) {
    }

    public record CourtResponse(UUID id,
                                String name,
                                String courtCode,
                                String courtType,
                                String surfaceType,
                                boolean indoor,
                                List<PriceRuleResponse> priceRules) {
    }

    /** @param dayOfWeek null nghia la ap dung moi ngay trong tuan */
    public record PriceRuleResponse(Short dayOfWeek,
                                    @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                    @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                    BigDecimal pricePerHour) {
    }
}
