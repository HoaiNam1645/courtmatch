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

    public record Item(UUID id, UUID venueId, String venueName, int rating, String comment, String reviewerName,
                       Instant createdAt, boolean edited, Reply reply) {
    }

    /** @param unanswered tong so danh gia hien thi chua tra loi cua moi san cua chu san (khong phu thuoc bo loc) */
    public record ListResponse(List<Item> items, int page, int size, long totalElements, int totalPages, long unanswered) {
    }

    public record ReplyRequest(
            @NotBlank(message = "Vui long nhap noi dung tra loi")
            @Size(max = 1000, message = "Cau tra loi toi da 1000 ky tu") String content) {
    }
}
