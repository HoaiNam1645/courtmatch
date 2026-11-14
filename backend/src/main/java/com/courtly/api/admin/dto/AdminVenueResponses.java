package com.courtly.api.admin.dto;

import com.courtly.api.owner.dto.OwnerVenueDetailResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 2.3.34 - quan tri vien duyet san cau long. */
public final class AdminVenueResponses {

    private AdminVenueResponses() {
    }

    /**
     * @param status         pending, active, inactive, rejected
     * @param approvalStatus pending, approved, rejected
     */
    public record Summary(UUID id, String name, String district, String address, String ownerName, String ownerEmail,
                          String status, String approvalStatus, long courtCount, Instant createdAt) {
    }

    /** @param approvalCounts du moi trang thai duyet (0 neu chua co) */
    public record ListResponse(List<Summary> content, int page, int size, long totalElements, int totalPages,
                               Map<String, Long> approvalCounts) {
    }

    public record Owner(UUID id, String fullName, String email, String phone) {
    }

    public record Image(String url, String caption, boolean cover) {
    }

    public record Hours(int dayOfWeek, boolean closed, LocalTime openTime, LocalTime closeTime) {
    }

    /** @param activeRules so quy tac gia con hieu luc; minPrice/maxPrice null neu chua co gia */
    public record CourtPrice(UUID courtId, String name, String courtCode, String status, int activeRules,
                             BigDecimal minPrice, BigDecimal maxPrice) {
    }

    /** @param code location, active_court, operating_hours, prices */
    public record Check(String code, String label, boolean ok) {
    }

    /**
     * @param approvedBy ten quan tri vien duyet; null neu chua duyet
     * @param canApprove san dang cho duyet va dat du dieu kien hien thi
     */
    public record Detail(OwnerVenueDetailResponse venue, Owner owner, List<Image> images, List<Hours> hours,
                         List<CourtPrice> courtPrices, List<Check> checks, boolean canApprove,
                         String approvedBy, Instant approvedAt) {
    }
}
