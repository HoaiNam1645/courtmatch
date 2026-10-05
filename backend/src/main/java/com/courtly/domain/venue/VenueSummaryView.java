package com.courtly.domain.venue;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Du lieu mot the san trong danh sach tim kiem (2.1.13, 2.1.15).
 *
 * <p>Tat ca cac truong duoc lay trong MOT cau truy van, khong lazy-load them.
 */
public interface VenueSummaryView {

    UUID getId();

    String getSlug();

    String getName();

    String getDistrict();

    String getAddress();

    String getPhone();

    BigDecimal getLatitude();

    BigDecimal getLongitude();

    BigDecimal getAverageRating();

    Integer getReviewCount();

    String getCoverImageUrl();

    /** So san con dang hoat dong. Chua phai so san CON TRONG - viec do thuoc 2.1.26. */
    Integer getCourtCount();

    BigDecimal getMinPricePerHour();

    BigDecimal getMaxPricePerHour();

    LocalTime getTodayOpenTime();

    LocalTime getTodayCloseTime();

    Boolean getClosedToday();

    /** NULL khi lan goi khong kem toa do nguoi dung. */
    Double getDistanceMeters();
}
