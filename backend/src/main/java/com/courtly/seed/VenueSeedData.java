package com.courtly.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * Cau truc cua file {@code classpath:seed/venues.json}.
 *
 * <p>File nay duoc sinh tu mock API cua frontend ({@code user-web/public/api/home.json})
 * nen id cac san trong du lieu mau khop voi venue-01 ... venue-06 ma giao dien dang dung.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VenueSeedData(List<ServiceSeed> services, List<VenueSeed> venues) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServiceSeed(String code, String name, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VenueSeed(String key,
                            String ownerKey,
                            String name,
                            String slug,
                            String description,
                            String address,
                            String district,
                            String province,
                            String phone,
                            String email,
                            double latitude,
                            double longitude,
                            BigDecimal averageRating,
                            int reviewCount,
                            List<OperatingHoursSeed> operatingHours,
                            List<ImageSeed> images,
                            List<VenueServiceSeed> services,
                            List<CourtSeed> courts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OperatingHoursSeed(short dayOfWeek, String openTime, String closeTime, boolean closed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageSeed(String key, String imageUrl, String caption, int displayOrder, boolean cover) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VenueServiceSeed(String code, BigDecimal price, String note) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CourtSeed(String key,
                            String name,
                            String courtCode,
                            String courtType,
                            String surfaceType,
                            boolean indoor,
                            List<PriceRuleSeed> priceRules) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceRuleSeed(Short dayOfWeek, String startTime, String endTime, BigDecimal pricePerHour) {
    }
}
