package com.courtly.api.dispute.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Chi tiet mot bao cao cua nguoi choi, kem dong thoi gian trao doi (2.3.90).
 *
 * <p>Thiet ke khong co bang tin nhan rieng nen {@code timeline} duoc ghep tu ho so tranh chap: tao bao cao, cac lan bo sung,
 * giai trinh cua chu san va quyet dinh cua quan tri vien.
 *
 * @param canSupplement      con bo sung thong tin duoc (dang xu ly va chua du so lan)
 * @param canCancel          huy duoc (chu san chua giai trinh)
 * @param evidenceRemaining  so anh con co the them (tong toi da 10 anh cua nguoi bao cao)
 */
public record DisputeDetailResponse(UUID id, String category, String categoryLabel, String status, boolean closed,
                                    boolean refundRequested, BigDecimal refundAmount, Instant createdAt,
                                    Instant ownerResponseDeadline, BookingInfo booking, List<TimelineEntry> timeline,
                                    boolean canSupplement, boolean canCancel, int supplementsRemaining, int evidenceRemaining) {

    public record BookingInfo(UUID id, String bookingCode, String venueName, String venueSlug, String courtName,
                              Instant startTime, Instant endTime, BigDecimal payableAmount) {
    }

    /**
     * @param type  created, supplement, owner_response hoac resolution
     * @param actor you (nguoi bao cao), owner hoac admin
     * @param label ket luan (chi voi resolution), vi du refund_full
     */
    public record TimelineEntry(String type, String actor, Instant at, String text, String label,
                                List<DisputeEvidenceResponse> evidences) {
    }
}
