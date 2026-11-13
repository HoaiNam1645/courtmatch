package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.util.List;

/** Danh muc lua chon cho form them / sua san (2.3.3, 2.3.4). */
public record VenueFormOptionsResponse(List<String> districts,
                                       List<Option> services,
                                       List<Option> courtTypes,
                                       List<Option> surfaceTypes,
                                       Bounds bounds,
                                       int maxCourts) {

    public record Option(String value, String label) {
    }

    /** Khung toa do hop le cua Da Nang, dung de bao loi som o frontend. */
    public record Bounds(BigDecimal minLatitude, BigDecimal maxLatitude,
                         BigDecimal minLongitude, BigDecimal maxLongitude) {
    }
}
