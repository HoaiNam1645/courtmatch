package com.courtly.api.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 2.3.36 - quan tri vien xu ly bao cao nguoi dung. */
public final class AdminReportResponses {

    private AdminReportResponses() {
    }

    public record Person(UUID id, String fullName, String email) {
    }

    /** @param exists false khi tai khoan duoc bao cao khong con trong he thong */
    public record Target(UUID id, String fullName, String email, String status, boolean exists) {
    }

    /** @param status pending, reviewing, resolved, rejected */
    public record Summary(UUID id, String status, String reason, String descriptionPreview, Person reporter, Target target,
                          Instant createdAt, Instant handledAt, String handledBy) {
    }

    /** @param statusCounts du moi trang thai (0 neu chua co); reasons la cac ly do da co trong du lieu */
    public record ListResponse(List<Summary> content, int page, int size, long totalElements, int totalPages,
                               Map<String, Long> statusCounts, List<String> reasons) {
    }

    /** Bao cao khac nham vao cung tai khoan. */
    public record Related(UUID id, String status, String reason, String reporterName, String handlingAction, Instant createdAt) {
    }

    /** @param action warning, account_banned, account_inactive, other (resolved) hoac null (rejected) */
    public record Handling(String action, String note, String handledBy, Instant handledAt) {
    }

    /**
     * @param roles          vai tro cua tai khoan duoc bao cao
     * @param totalReceived  tong bao cao da nhan (ke ca bao cao nay)
     * @param canStart       pending va khong phai bao cao ve chinh quan tri vien
     * @param canConclude    dang reviewing va khong phai bao cao ve chinh quan tri vien
     * @param canLockAccount co quyen user.manage va tai khoan dang active
     * @param selfTarget     bao cao nham vao chinh quan tri vien dang xem
     */
    public record Detail(UUID id, String status, String reason, String description, Person reporter, Target target,
                         List<String> roles, long totalReceived, List<Related> related, Instant createdAt,
                         Handling handling, boolean canStart, boolean canConclude, boolean canLockAccount, boolean selfTarget) {
    }
}
