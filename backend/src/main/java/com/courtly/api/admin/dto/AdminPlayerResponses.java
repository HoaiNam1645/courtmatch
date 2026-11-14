package com.courtly.api.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 2.3.31 - quan ly tai khoan nguoi choi. */
public final class AdminPlayerResponses {

    private AdminPlayerResponses() {
    }

    /** @param skillLevel beginner, intermediate, advanced, professional; null neu chua khai bao */
    public record Summary(UUID id, String fullName, String email, String phone, String status, String skillLevel,
                          long bookingCount, Instant lastLoginAt, Instant createdAt) {
    }

    public record ListResponse(List<Summary> content, int page, int size, long totalElements, int totalPages,
                               Map<String, Long> statusCounts) {
    }

    public record RecentBooking(UUID id, String bookingCode, String venueName, String status, BigDecimal totalAmount,
                                Instant createdAt) {
    }

    /**
     * @param reportCount so lan bi nguoi khac bao cao
     * @param bookingsByStatus so booking theo tung trang thai
     */
    public record Detail(AdminUserResponses.Detail account, String gender, LocalDate dateOfBirth, String skillLevel,
                         BigDecimal skillScore, String dominantHand, String bio, long bookingCount,
                         Map<String, Long> bookingsByStatus, long matchCount, long reportCount,
                         List<RecentBooking> recentBookings) {
    }
}
