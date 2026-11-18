package com.courtly.api.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 2.3.84 - quan tri vien xu ly bao cao ve danh gia san cua nguoi choi (do chu san bao cao).
 *
 * <p>Chi hien ten (khong email, so dien thoai) cua nguoi danh gia, dung nhu thong tin da hien thi cong khai.
 */
public final class AdminVenueReviewReportResponses {

    private AdminVenueReviewReportResponses() {
    }

    public record Reply(String content, Instant repliedAt) {
    }

    /**
     * @param exists false khi danh gia da bi xoa
     * @param status visible, hidden, reported
     * @param reply  cau tra loi cong khai cua chu san; null khi chua co (chi co o chi tiet)
     */
    public record Review(UUID id, boolean exists, Short rating, String comment, String reviewerName, UUID venueId,
                         String venueName, String status, Instant createdAt, Reply reply) {
    }

    public record Summary(UUID id, String status, String reason, String reasonLabel, String reporterName, Review review,
                          Instant createdAt, Instant handledAt, String handledBy) {
    }

    public record ListResponse(List<Summary> content, int page, int size, long totalElements, int totalPages,
                               Map<String, Long> statusCounts, List<ReasonOption> reasons) {
    }

    public record ReasonOption(String code, String label) {
    }

    public record Related(UUID id, String status, String reason, String reasonLabel, String reporterName,
                          String handlingAction, Instant createdAt) {
    }

    /** @param action review_hidden, review_kept hoac null */
    public record Handling(String action, String note, String handledBy, Instant handledAt) {
    }

    /**
     * @param canStart    pending va admin khong phai mot ben cua danh gia
     * @param canConclude dang reviewing va admin khong phai mot ben cua danh gia
     * @param canHide     co the an danh gia (danh gia con ton tai va chua bi an)
     * @param canRestore  da an danh gia theo bao cao nay va danh gia van dang an
     * @param involved    admin la nguoi viet danh gia hoac chu san cua san
     */
    public record Detail(UUID id, String status, String reason, String reasonLabel, String description, String reporterName,
                         Review review, List<Related> related, long totalReports, Instant createdAt, Handling handling,
                         boolean canStart, boolean canConclude, boolean canHide, boolean canRestore, boolean involved) {
    }
}
