package com.courtly.api.owner.dto;

import com.courtly.api.dispute.dto.DisputeDetailResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 2.3.93 - 2.3.96: chu san xem bao cao ve don thuoc san cua minh va giai trinh. */
public final class OwnerDisputeResponses {

    private OwnerDisputeResponses() {
    }

    /**
     * @param overdue      qua han giai trinh (chi khi dang cho giai trinh)
     * @param expiringSoon con duoi 6 gio la het han giai trinh
     */
    public record Item(UUID id, UUID bookingId, String bookingCode, UUID venueId, String venueName, String playerName,
                       String category, String categoryLabel, String status, boolean closed, boolean refundRequested,
                       BigDecimal refundAmount, int evidenceCount, Instant createdAt, Instant ownerResponseDeadline,
                       boolean overdue, boolean expiringSoon, String resolution) {
    }

    /** @param awaiting so bao cao dang cho giai trinh; @param expiringSoon so bao cao con duoi 6 gio; @param overdue so bao cao da qua han */
    public record Counts(long awaiting, long expiringSoon, long overdue) {
    }

    public record ListResponse(List<Item> items, int page, int size, long totalElements, int totalPages, Counts counts) {
    }

    /** Nguoi choi ma chu san duoc biet: ho ten va so dien thoai da dang ky don, khong co email hay dia chi. */
    public record Player(UUID id, String fullName, String phone) {
    }

    public record Booking(UUID id, String bookingCode, UUID venueId, String venueName, String courtName, Instant startTime,
                          Instant endTime, BigDecimal payableAmount) {
    }

    /**
     * @param canRespond    con giai trinh duoc (cho giai trinh, chua qua han, chua giai trinh)
     * @param timeline      actor: player (nguoi choi), you (chu san), admin
     */
    public record Detail(UUID id, String category, String categoryLabel, String status, boolean closed, boolean refundRequested,
                         BigDecimal refundAmount, Instant createdAt, Instant ownerResponseDeadline, boolean overdue,
                         Player player, Booking booking, List<DisputeDetailResponse.TimelineEntry> timeline, boolean canRespond) {
    }
}
