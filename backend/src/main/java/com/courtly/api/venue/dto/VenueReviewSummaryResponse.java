package com.courtly.api.venue.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Tong hop danh gia dang hien thi cua san (2.3.80).
 *
 * @param average      diem trung binh lam tron 2 chu so; 0 khi chua co danh gia
 * @param distribution so danh gia theo tung muc "1".."5" (du ca nam khoa, 0 neu khong co)
 */
public record VenueReviewSummaryResponse(BigDecimal average, long total, Map<String, Long> distribution) {
}
