package com.courtly.api.owner.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Mot san trong danh sach "San cua toi" (2.3.3).
 *
 * @param status         pending, active, inactive, rejected
 * @param approvalStatus pending, approved, rejected - do quan tri vien duyet
 * @param courtCount     tong so san con, moi trang thai
 */
public record OwnerVenueSummaryResponse(UUID id,
                                        String name,
                                        String slug,
                                        String district,
                                        String address,
                                        String status,
                                        String approvalStatus,
                                        long courtCount,
                                        String coverImageUrl,
                                        Instant createdAt) {
}
