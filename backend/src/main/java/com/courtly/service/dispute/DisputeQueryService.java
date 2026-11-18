package com.courtly.service.dispute;

import com.courtly.api.dispute.dto.DisputeDetailResponse;
import com.courtly.api.dispute.dto.DisputeDetailResponse.BookingInfo;
import com.courtly.api.dispute.dto.DisputeDetailResponse.TimelineEntry;
import com.courtly.api.dispute.dto.DisputeEvidenceResponse;
import com.courtly.api.dispute.dto.DisputeSummaryResponse;
import com.courtly.common.PageResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.payment.BookingDispute;
import com.courtly.domain.payment.BookingDisputeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nguoi choi xem danh sach va chi tiet bao cao da gui (2.3.89, 2.3.90). Chi doc, chi bao cao cua chinh minh. */
@Service
@RequiredArgsConstructor
public class DisputeQueryService {

    public static final Set<String> ACTIVE = Set.of("open", "awaiting_owner_response", "reviewing");
    public static final Set<String> CLOSED = Set.of("resolved", "rejected", "cancelled");
    static final int MAX_SUPPLEMENTS = 10;

    private final BookingDisputeRepository disputeRepository;

    /** 2.3.89 - danh sach bao cao cua chinh minh; {@code status}: all, active hoac closed. */
    @Transactional(readOnly = true)
    public PageResponse<DisputeSummaryResponse> list(UUID userId, String status, int page, int size) {
        Set<String> statuses = switch (status == null || status.isBlank() ? "all" : status) {
            case "all" -> union();
            case "active" -> ACTIVE;
            case "closed" -> CLOSED;
            default -> throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai khong hop le.",
                    Map.of("status", "Chi nhan all, active hoac closed"));
        };
        Page<BookingDispute> disputes = disputeRepository.findForReporter(userId, statuses, PageRequest.of(page, size));
        return PageResponse.from(disputes, disputes.getContent().stream().map(DisputeQueryService::toSummary).toList());
    }

    /** 2.3.90 - chi tiet kem dong thoi gian; bao cao cua nguoi khac tra 404. */
    @Transactional(readOnly = true)
    public DisputeDetailResponse get(UUID userId, UUID disputeId) {
        BookingDispute dispute = disputeRepository.findWithParties(disputeId)
                .filter(item -> item.getReporter().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay bao cao."));
        return toDetail(dispute);
    }

    static DisputeDetailResponse toDetail(BookingDispute dispute) {
        return toDetail(dispute, false);
    }

    /**
     * {@code ownerView}: dong thoi gian nhin tu chu san (nguoi choi la "player", chu san la "you"); mac dinh nhin tu nguoi choi.
     */
    public static DisputeDetailResponse toDetail(BookingDispute dispute, boolean ownerView) {
        UUID id = dispute.getId();
        List<Map<String, Object>> evidences = dispute.getReporterEvidences() == null ? List.of() : dispute.getReporterEvidences();
        List<Map<String, Object>> updates = dispute.getReporterUpdates() == null ? List.of() : dispute.getReporterUpdates();

        List<TimelineEntry> timeline = new ArrayList<>();
        timeline.add(new TimelineEntry("created", ownerView ? "player" : "you", dispute.getCreatedAt(), dispute.getDescription(), null,
                DisputeEvidenceService.toResponses(id, evidences.stream().filter(item -> item.get("supplementId") == null).toList())));
        for (Map<String, Object> update : updates) {
            String updateId = (String) update.get("id");
            timeline.add(new TimelineEntry("supplement", ownerView ? "player" : "you", Instant.parse((String) update.get("createdAt")),
                    (String) update.get("content"), null,
                    DisputeEvidenceService.toResponses(id, evidences.stream().filter(item -> updateId.equals(item.get("supplementId"))).toList())));
        }
        if (dispute.getOwnerResponse() != null && dispute.getOwnerRespondedAt() != null) {
            timeline.add(new TimelineEntry("owner_response", ownerView ? "you" : "owner", dispute.getOwnerRespondedAt(), dispute.getOwnerResponse(), null,
                    DisputeEvidenceService.toResponses(id, dispute.getOwnerEvidences())));
        }
        if (dispute.getResolution() != null && dispute.getHandledAt() != null) {
            timeline.add(new TimelineEntry("resolution", "admin", dispute.getHandledAt(), dispute.getResolutionNote(), dispute.getResolution(),
                    DisputeEvidenceService.toResponses(id, dispute.getResolutionEvidences())));
        }
        timeline.sort(Comparator.comparing(TimelineEntry::at));

        boolean active = ACTIVE.contains(dispute.getStatus());
        boolean awaiting = "awaiting_owner_response".equals(dispute.getStatus());
        var booking = dispute.getBooking();
        BookingItem first = booking.getItems().stream().min(Comparator.comparing(BookingItem::getStartTime)).orElse(null);
        BookingItem last = booking.getItems().stream().max(Comparator.comparing(BookingItem::getEndTime)).orElse(null);
        return new DisputeDetailResponse(id, dispute.getCategory(), categoryLabel(dispute.getCategory()), dispute.getStatus(),
                CLOSED.contains(dispute.getStatus()), dispute.isRefundRequested(), dispute.getRequestedRefundAmount(), dispute.getCreatedAt(),
                dispute.getOwnerResponseDeadline(),
                new BookingInfo(booking.getId(), booking.getBookingCode(), booking.getVenue().getName(), booking.getVenue().getSlug(),
                        first == null ? null : first.getCourt().getName(), first == null ? null : first.getStartTime(),
                        last == null ? null : last.getEndTime(), booking.getPayableAmount()),
                timeline, active && updates.size() < MAX_SUPPLEMENTS,
                awaiting && dispute.getOwnerResponse() == null, Math.max(0, MAX_SUPPLEMENTS - updates.size()),
                Math.max(0, DisputeEvidenceService.MAX_TOTAL - evidences.size()));
    }

    static DisputeSummaryResponse toSummary(BookingDispute dispute) {
        int evidenceCount = dispute.getReporterEvidences() == null ? 0 : dispute.getReporterEvidences().size();
        return new DisputeSummaryResponse(dispute.getId(), dispute.getBooking().getId(), dispute.getBooking().getBookingCode(),
                dispute.getBooking().getVenue().getName(), dispute.getCategory(), categoryLabel(dispute.getCategory()), dispute.getStatus(),
                CLOSED.contains(dispute.getStatus()), dispute.isRefundRequested(), dispute.getRequestedRefundAmount(), evidenceCount,
                dispute.getCreatedAt(), "awaiting_owner_response".equals(dispute.getStatus()) ? dispute.getOwnerResponseDeadline() : null,
                dispute.getResolution());
    }

    private static String categoryLabel(String code) {
        var category = DisputeCategories.find(code);
        return category == null ? code : category.label();
    }

    private static Set<String> union() {
        Set<String> all = new java.util.HashSet<>(ACTIVE);
        all.addAll(CLOSED);
        return all;
    }
}
