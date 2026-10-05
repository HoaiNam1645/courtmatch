package com.courtly.api.venue.dto;

import com.courtly.domain.venue.VenueSummaryView;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Mot the san trong danh sach (2.1.13, 2.1.15) va trong panel xem nhanh tren ban do (2.1.17).
 *
 * @param courtCount     so san con dang hoat dong; chua phai so san CON TRONG (2.1.26 moi lam)
 * @param distanceKm     null khi lan goi khong kem toa do nguoi dung
 * @param closedToday    hom nay nghi, khi do todayOpenTime/todayCloseTime co the null
 */
public record VenueSummaryResponse(UUID id,
                                   String slug,
                                   String name,
                                   String district,
                                   String address,
                                   String phone,
                                   BigDecimal latitude,
                                   BigDecimal longitude,
                                   String coverImageUrl,
                                   BigDecimal averageRating,
                                   int reviewCount,
                                   int courtCount,
                                   BigDecimal minPricePerHour,
                                   BigDecimal maxPricePerHour,
                                   @JsonFormat(pattern = "HH:mm") LocalTime todayOpenTime,
                                   @JsonFormat(pattern = "HH:mm") LocalTime todayCloseTime,
                                   boolean closedToday,
                                   BigDecimal distanceKm) {

    public static VenueSummaryResponse from(VenueSummaryView view) {
        return new VenueSummaryResponse(
                view.getId(),
                view.getSlug(),
                view.getName(),
                view.getDistrict(),
                view.getAddress(),
                view.getPhone(),
                view.getLatitude(),
                view.getLongitude(),
                view.getCoverImageUrl(),
                view.getAverageRating(),
                view.getReviewCount() == null ? 0 : view.getReviewCount(),
                view.getCourtCount() == null ? 0 : view.getCourtCount(),
                view.getMinPricePerHour(),
                view.getMaxPricePerHour(),
                view.getTodayOpenTime(),
                view.getTodayCloseTime(),
                Boolean.TRUE.equals(view.getClosedToday()),
                toKilometres(view.getDistanceMeters()));
    }

    /** Doi met sang km, lam tron mot chu so thap phan dung nhu giao dien hien thi. */
    private static BigDecimal toKilometres(Double metres) {
        return metres == null
                ? null
                : BigDecimal.valueOf(metres / 1000).setScale(1, RoundingMode.HALF_UP);
    }
}
