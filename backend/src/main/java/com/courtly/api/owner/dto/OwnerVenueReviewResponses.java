package com.courtly.api.owner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 2.3.81 - chu san xem danh gia san cua minh va tra loi cong khai. */
public final class OwnerVenueReviewResponses {

    private OwnerVenueReviewResponses() {
    }

    public record Reply(String content, Instant repliedAt, boolean edited) {
    }

    /**
     * Bao cao moi nhat cua chinh chu san ve danh gia nay (2.3.83).
     *
     * @param status         pending, reviewing, resolved hoac rejected
     * @param handlingAction review_hidden (da an danh gia) hoac review_kept (giu nguyen); null khi chua ket luan
     */
    public record Report(UUID id, String reason, String reasonLabel, String status, String handlingAction,
                         Instant createdAt, Instant handledAt) {
    }

    /** @param status visible, hidden hoac reported: trang thai hien thi cong khai cua danh gia */
    public record Item(UUID id, UUID venueId, String venueName, int rating, String comment, String reviewerName,
                       Instant createdAt, boolean edited, String status, Reply reply, Report report) {
    }

    /** Mot ly do bao cao trong danh muc (2.3.83). */
    public record ReportReason(String code, String label, String hint, boolean descriptionRequired) {
    }

    public record ReportRequest(
            @NotBlank(message = "Vui long chon ly do bao cao") String reason,
            @Size(max = 1000, message = "Mo ta toi da 1000 ky tu") String description) {
    }

    /** @param unanswered tong so danh gia hien thi chua tra loi cua moi san cua chu san (khong phu thuoc bo loc) */
    public record ListResponse(List<Item> items, int page, int size, long totalElements, int totalPages, long unanswered) {
    }

    public record ReplyRequest(
            @NotBlank(message = "Vui long nhap noi dung tra loi")
            @Size(max = 1000, message = "Cau tra loi toi da 1000 ky tu") String content) {
    }
}
