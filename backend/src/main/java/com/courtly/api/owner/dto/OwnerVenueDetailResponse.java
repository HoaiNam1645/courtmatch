package com.courtly.api.owner.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Thong tin san ma chu san xem va sua (2.3.3, 2.3.4), ke ca san chua duoc duyet.
 *
 * @param rejectionReason ly do quan tri vien tu choi (2.3.35); chi co y nghia khi approvalStatus = rejected
 */
public record OwnerVenueDetailResponse(UUID id,
                                       String name,
                                       String slug,
                                       String description,
                                       String address,
                                       String ward,
                                       String district,
                                       String province,
                                       BigDecimal latitude,
                                       BigDecimal longitude,
                                       String phone,
                                       String email,
                                       String status,
                                       String approvalStatus,
                                       List<ServiceItem> services,
                                       List<CourtItem> courts,
                                       Instant createdAt,
                                       Instant updatedAt,
                                       String rejectionReason,
                                       Instant rejectedAt) {

    /** @param price null = khong niem yet gia, 0 = mien phi */
    public record ServiceItem(String code, String name, BigDecimal price, String note) {
    }

    /** @param status active, inactive, maintenance */
    public record CourtItem(UUID id, String name, String courtCode, String courtType,
                            String surfaceType, boolean indoor, String status) {
    }
}
