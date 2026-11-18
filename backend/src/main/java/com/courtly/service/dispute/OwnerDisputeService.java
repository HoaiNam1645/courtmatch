package com.courtly.service.dispute;

import com.courtly.api.dispute.dto.DisputeDetailResponse;
import com.courtly.api.owner.dto.OwnerDisputeResponses.Booking;
import com.courtly.api.owner.dto.OwnerDisputeResponses.Counts;
import com.courtly.api.owner.dto.OwnerDisputeResponses.Detail;
import com.courtly.api.owner.dto.OwnerDisputeResponses.Item;
import com.courtly.api.owner.dto.OwnerDisputeResponses.ListResponse;
import com.courtly.api.owner.dto.OwnerDisputeResponses.Player;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.payment.BookingDispute;
import com.courtly.domain.payment.BookingDisputeRepository;
import com.courtly.service.owner.OwnedVenueLookup;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chu san xem bao cao ve don thuoc san cua minh (2.3.93, 2.3.94). Chi doc; giai trinh la 2.3.95-96.
 *
 * <p>Chi bao cao ve don thuoc san cua chinh chu san (san khac 404). Chu san thay ho ten va so dien thoai nguoi choi dat don,
 * khong thay email hay dia chi.
 */
@Service
@RequiredArgsConstructor
public class OwnerDisputeService {

    static final Duration EXPIRING_SOON = Duration.ofHours(6);
    private static final Set<String> AWAITING = Set.of("open", "awaiting_owner_response");

    private final BookingDisputeRepository disputeRepository;
    private final OwnedVenueLookup ownedVenueLookup;

    @Transactional(readOnly = true)
    public ListResponse list(UUID ownerId, UUID venueId, String status, int page, int size) {
        if (venueId != null) {
            ownedVenueLookup.requireVenue(ownerId, venueId);
        }
        Set<String> statuses = switch (status == null || status.isBlank() ? "all" : status) {
            case "all" -> allStatuses();
            case "awaiting" -> AWAITING;
            case "answered" -> Set.of("reviewing");
            case "closed" -> DisputeQueryService.CLOSED;
            default -> throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai khong hop le.",
                    Map.of("status", "Chi nhan all, awaiting, answered hoac closed"));
        };
        Instant now = Instant.now();
        Page<BookingDispute> disputes = disputeRepository.findForOwner(ownerId, venueId, statuses, PageRequest.of(page, size));
        long awaiting = disputeRepository.countAwaitingForOwner(ownerId);
        long soon = disputeRepository.countAwaitingBetween(ownerId, now, now.plus(EXPIRING_SOON));
        long overdue = disputeRepository.countOverdueForOwner(ownerId, now);
        return new ListResponse(disputes.getContent().stream().map(item -> toItem(item, now)).toList(),
                disputes.getNumber(), disputes.getSize(), disputes.getTotalElements(), disputes.getTotalPages(),
                new Counts(awaiting, soon, overdue));
    }

    @Transactional(readOnly = true)
    public Detail get(UUID ownerId, UUID disputeId) {
        BookingDispute dispute = disputeRepository.findForOwnerById(disputeId, ownerId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay bao cao."));
        return toDetail(dispute, Instant.now());
    }

    static Detail toDetail(BookingDispute dispute, Instant now) {
        DisputeDetailResponse view = DisputeQueryService.toDetail(dispute, true);
        var booking = dispute.getBooking();
        BookingItem first = booking.getItems().stream().min(Comparator.comparing(BookingItem::getStartTime)).orElse(null);
        BookingItem last = booking.getItems().stream().max(Comparator.comparing(BookingItem::getEndTime)).orElse(null);
        boolean awaiting = AWAITING.contains(dispute.getStatus());
        boolean overdue = isOverdue(dispute, now);
        return new Detail(dispute.getId(), dispute.getCategory(), view.categoryLabel(), dispute.getStatus(), view.closed(),
                dispute.isRefundRequested(), dispute.getRequestedRefundAmount(), dispute.getCreatedAt(),
                dispute.getOwnerResponseDeadline(), overdue,
                new Player(dispute.getReporter().getId(), dispute.getReporter().getFullName(), dispute.getReporter().getPhone()),
                new Booking(booking.getId(), booking.getBookingCode(), booking.getVenue().getId(), booking.getVenue().getName(),
                        first == null ? null : first.getCourt().getName(), first == null ? null : first.getStartTime(),
                        last == null ? null : last.getEndTime(), booking.getPayableAmount()),
                view.timeline(), awaiting && !overdue && dispute.getOwnerResponse() == null);
    }

    static Item toItem(BookingDispute dispute, Instant now) {
        var booking = dispute.getBooking();
        boolean awaiting = AWAITING.contains(dispute.getStatus());
        boolean overdue = isOverdue(dispute, now);
        boolean soon = awaiting && !overdue && dispute.getOwnerResponseDeadline() != null
                && dispute.getOwnerResponseDeadline().isBefore(now.plus(EXPIRING_SOON));
        int evidenceCount = dispute.getReporterEvidences() == null ? 0 : dispute.getReporterEvidences().size();
        var category = DisputeCategories.find(dispute.getCategory());
        return new Item(dispute.getId(), booking.getId(), booking.getBookingCode(), booking.getVenue().getId(), booking.getVenue().getName(),
                dispute.getReporter().getFullName(), dispute.getCategory(), category == null ? dispute.getCategory() : category.label(),
                dispute.getStatus(), DisputeQueryService.CLOSED.contains(dispute.getStatus()), dispute.isRefundRequested(),
                dispute.getRequestedRefundAmount(), evidenceCount, dispute.getCreatedAt(),
                awaiting ? dispute.getOwnerResponseDeadline() : null, overdue, soon, dispute.getResolution());
    }

    private static boolean isOverdue(BookingDispute dispute, Instant now) {
        return AWAITING.contains(dispute.getStatus()) && dispute.getOwnerResponseDeadline() != null
                && !dispute.getOwnerResponseDeadline().isAfter(now);
    }

    private static Set<String> allStatuses() {
        Set<String> all = new java.util.HashSet<>(AWAITING);
        all.add("reviewing");
        all.addAll(DisputeQueryService.CLOSED);
        return all;
    }
}
