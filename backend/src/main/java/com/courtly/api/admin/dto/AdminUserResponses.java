package com.courtly.api.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 2.3.28 - quan ly tai khoan nguoi dung. */
public final class AdminUserResponses {

    private AdminUserResponses() {
    }

    /** @param status active, inactive, banned, pending */
    public record Summary(UUID id, String fullName, String email, String phone, String status, List<String> roles,
                          boolean emailVerified, boolean phoneVerified, Instant lastLoginAt, Instant createdAt) {
    }

    /** @param statusCounts du moi trang thai (0 neu chua co), tinh tren toan bo tai khoan */
    public record ListResponse(List<Summary> content, int page, int size, long totalElements, int totalPages,
                               Map<String, Long> statusCounts) {
    }

    /**
     * @param actor ten nguoi thuc hien; null khi he thong
     * @param reason ly do da nhap; null neu khong co
     */
    public record StatusChange(String status, String reason, String actor, Instant at) {
    }

    public record Detail(UUID id, String fullName, String email, String phone, String avatarUrl, String status,
                         List<String> roles, boolean emailVerified, Instant emailVerifiedAt, boolean phoneVerified,
                         Instant phoneVerifiedAt, boolean passwordChangeRequired, Instant lastLoginAt,
                         Instant createdAt, Instant updatedAt, StatusChange lastStatusChange) {
    }
}
