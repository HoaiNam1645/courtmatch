package com.courtly.api.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 2.3.37 - quan tri vien xu ly bao cao ve danh gia doi tac.
 *
 * <p>Chi hien ten (khong email, so dien thoai) cua nguoi danh gia va nguoi duoc danh gia, dung
 * nhu thong tin da hien thi cong khai trong danh gia.
 */
public final class AdminReviewReportResponses {

    private AdminReviewReportResponses() {
    }

    /**
     * @param exists  false khi danh gia da bi xoa (vi du nguoi danh gia khong con)
     * @param status  visible, hidden, reported
     */
    public record Review(UUID id, boolean exists, Short rating, String comment, String reviewerName, String reviewedName,
                         UUID matchId, String status, Instant createdAt) {
    }

    public record Summary(UUID id, String status, String reason, String reporterName, Review review,
                          Instant createdAt, Instant handledAt, String handledBy) {
    }

    public record ListResponse(List<Summary> content, int page, int size, long totalElements, int totalPages,
                               Map<String, Long> statusCounts, List<String> reasons) {
    }

    public record Related(UUID id, String status, String reason, String reporterName, String handlingAction, Instant createdAt) {
    }

    /** @param action review_hidden, review_kept hoac null */
    public record Handling(String action, String note, String handledBy, Instant handledAt) {
    }

    /**
     * @param description  mo ta cua nguoi bao cao
     * @param canStart     pending va admin khong phai mot ben cua danh gia
     * @param canConclude  dang reviewing va admin khong phai mot ben cua danh gia
     * @param canHide      co the go danh gia (danh gia con ton tai va chua bi an)
     * @param canRestore   da go danh gia theo bao cao nay va danh gia van dang an
     * @param involved     admin la nguoi danh gia hoac nguoi duoc danh gia
     */
    public record Detail(UUID id, String status, String reason, String description, String reporterName, Review review,
                         List<Related> related, long totalReports, Instant createdAt, Handling handling,
                         boolean canStart, boolean canConclude, boolean canHide, boolean canRestore, boolean involved) {
    }
}
