package com.courtly.service.owner;

import com.courtly.api.owner.dto.OwnerVenueReviewResponses.Item;
import com.courtly.api.owner.dto.OwnerVenueReviewResponses.ListResponse;
import com.courtly.api.owner.dto.OwnerVenueReviewResponses.Reply;
import com.courtly.common.enums.ReportStatus;
import com.courtly.common.enums.ReportTargetType;
import com.courtly.common.enums.ReviewStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.review.VenueReview;
import com.courtly.domain.review.VenueReviewReply;
import com.courtly.domain.review.VenueReviewReplyRepository;
import com.courtly.domain.review.VenueReviewRepository;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.venue.VenueRepository;
import com.courtly.service.notification.ReviewNotifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.81 - chu san xem danh gia hien thi cua cac san cua minh va tra loi cong khai.
 *
 * <p>Chi tra loi danh gia dang hien thi cua san thuoc ve minh, moi danh gia mot cau (sua cau tra loi la 2.3.82).
 */
@Service
@RequiredArgsConstructor
public class OwnerVenueReviewService {

    static final List<ReportStatus> OPEN_REPORT_STATUSES = List.of(ReportStatus.PENDING, ReportStatus.REVIEWING);
    private static final Set<String> FILTERS = Set.of("all", "answered", "unanswered", "reported");

    private final VenueRepository venueRepository;
    private final OwnedVenueLookup ownedVenueLookup;
    private final VenueReviewRepository reviewRepository;
    private final VenueReviewReplyRepository replyRepository;
    private final UserRepository userRepository;
    private final ReviewNotifier reviewNotifier;
    private final com.courtly.domain.review.ReportRepository reportRepository;

    /** Danh gia hien thi cua cac san cua chu san; {@code venueId} null = moi san. */
    @Transactional(readOnly = true)
    public ListResponse list(UUID ownerId, UUID venueId, String filter, Integer rating, int page, int size) {
        String normalized = filter == null || filter.isBlank() ? "all" : filter;
        if (!FILTERS.contains(normalized)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Bo loc khong hop le.",
                    Map.of("filter", "Chi nhan all, answered, unanswered hoac reported"));
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "So sao loc khong hop le.",
                    Map.of("rating", "So sao tu 1 den 5"));
        }
        List<UUID> ownedIds = venueRepository.findIdsByOwnerId(ownerId);
        List<UUID> venueIds;
        if (venueId == null) {
            venueIds = ownedIds;
        } else {
            ownedVenueLookup.requireVenue(ownerId, venueId);
            venueIds = List.of(venueId);
        }
        if (venueIds.isEmpty()) {
            return new ListResponse(List.of(), page, size, 0, 0, 0);
        }

        // "reported": cac danh gia chu san da bao cao, ke ca da bi an de thay ket qua bao cao.
        Page<VenueReview> reviews = "reported".equals(normalized)
                ? reviewRepository.findReportedByOwner(ownerId, ReportTargetType.VENUE_REVIEW, venueIds,
                rating == null ? 0 : rating, PageRequest.of(page, size))
                : reviewRepository.findForOwner(venueIds, ReviewStatus.VISIBLE,
                rating == null ? 0 : rating, normalized, PageRequest.of(page, size));
        Map<UUID, VenueReviewReply> replies = reviews.isEmpty() ? Map.of()
                : replyRepository.findByReviewIds(reviews.getContent().stream().map(VenueReview::getId).toList()).stream()
                .collect(Collectors.toMap(reply -> reply.getReview().getId(), reply -> reply));
        long unanswered = reviewRepository.countUnanswered(ownedIds, ReviewStatus.VISIBLE);
        Map<UUID, com.courtly.domain.review.Report> reports = new java.util.HashMap<>();
        if (!reviews.isEmpty()) {
            // Moi nhat truoc: giu bao cao dau tien gap cua tung danh gia.
            reportRepository.findByReporterAndTargets(ownerId, ReportTargetType.VENUE_REVIEW,
                    reviews.getContent().stream().map(VenueReview::getId).toList())
                    .forEach(report -> reports.putIfAbsent(report.getTargetId(), report));
        }
        return new ListResponse(reviews.getContent().stream()
                .map(review -> toItem(review, replies.get(review.getId()), reports.get(review.getId()))).toList(),
                reviews.getNumber(), reviews.getSize(), reviews.getTotalElements(), reviews.getTotalPages(), unanswered);
    }

    /** Tra loi cong khai mot danh gia; thong bao cho nguoi da danh gia. */
    @Transactional
    public Item reply(UUID ownerId, UUID reviewId, String content) {
        VenueReview review = reviewRepository.findWithVenueAndUser(reviewId)
                .filter(item -> item.getVenue().getOwner().getId().equals(ownerId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay danh gia nay."));
        if (review.getStatus() != ReviewStatus.VISIBLE) {
            throw ApiException.conflict(ErrorCode.VENUE_REVIEW_STATE_INVALID,
                    "Chi tra loi duoc danh gia dang hien thi.", Map.of());
        }
        if (replyRepository.findByReviewId(reviewId).isPresent()) {
            throw ApiException.conflict(ErrorCode.VENUE_REVIEW_STATE_INVALID,
                    "Danh gia nay da co cau tra loi. Hay sua cau tra loi thay vi tra loi lai.", Map.of());
        }
        String text = requireContent(content);
        VenueReviewReply reply = new VenueReviewReply();
        reply.setId(UUID.randomUUID());
        reply.setReview(review);
        reply.setOwner(userRepository.getReferenceById(ownerId));
        reply.setContent(text);
        replyRepository.saveAndFlush(reply);
        reviewNotifier.replied(review);
        return toItem(review, reply, latestReport(ownerId, review));
    }

    /** 2.3.83 - chu san bao cao mot danh gia khong phu hop; danh gia van hien thi cho toi khi admin ket luan. */
    @Transactional
    public Item report(UUID ownerId, UUID reviewId, String reasonCode, String description) {
        VenueReview review = reviewRepository.findWithVenueAndUser(reviewId)
                .filter(item -> item.getVenue().getOwner().getId().equals(ownerId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay danh gia nay."));
        com.courtly.api.owner.dto.OwnerVenueReviewResponses.ReportReason reason = VenueReviewReportReasons.find(reasonCode);
        if (reason == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ly do bao cao khong hop le.",
                    Map.of("reason", "Hay chon mot ly do trong danh muc"));
        }
        String text = description == null ? null : description.trim();
        if (text != null && text.isEmpty()) text = null;
        if (reason.descriptionRequired() && (text == null || text.length() < 10)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Vui long mo ta ly do bao cao.",
                    Map.of("description", "Mo ta it nhat 10 ky tu khi chon ly do khac"));
        }
        if (review.getStatus() != ReviewStatus.VISIBLE) {
            throw ApiException.conflict(ErrorCode.VENUE_REVIEW_STATE_INVALID,
                    "Chi bao cao duoc danh gia dang hien thi.", Map.of());
        }
        if (reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(ownerId, ReportTargetType.VENUE_REVIEW,
                reviewId, OPEN_REPORT_STATUSES)) {
            throw ApiException.conflict(ErrorCode.VENUE_REVIEW_STATE_INVALID,
                    "Ban da bao cao danh gia nay va dang cho xu ly.", Map.of());
        }
        com.courtly.domain.review.Report report = new com.courtly.domain.review.Report();
        report.setId(UUID.randomUUID());
        report.setReporter(userRepository.getReferenceById(ownerId));
        report.setTargetType(ReportTargetType.VENUE_REVIEW);
        report.setTargetId(reviewId);
        report.setReason(reasonCode);
        report.setDescription(text);
        report.setStatus(ReportStatus.PENDING);
        reportRepository.saveAndFlush(report);
        return toItem(review, replyRepository.findByReviewId(reviewId).orElse(null), report);
    }

    /** 2.3.82 - sua cau tra loi da dang; chi khi danh gia con hien thi va da co cau tra loi. */
    @Transactional
    public Item updateReply(UUID ownerId, UUID reviewId, String content) {
        VenueReview review = reviewRepository.findWithVenueAndUser(reviewId)
                .filter(item -> item.getVenue().getOwner().getId().equals(ownerId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay danh gia nay."));
        if (review.getStatus() != ReviewStatus.VISIBLE) {
            throw ApiException.conflict(ErrorCode.VENUE_REVIEW_STATE_INVALID,
                    "Chi sua duoc cau tra loi cua danh gia dang hien thi.", Map.of());
        }
        VenueReviewReply reply = replyRepository.findByReviewId(reviewId)
                .orElseThrow(() -> ApiException.conflict(ErrorCode.VENUE_REVIEW_STATE_INVALID,
                        "Danh gia nay chua co cau tra loi de sua.", Map.of()));
        reply.setContent(requireContent(content));
        replyRepository.saveAndFlush(reply);
        return toItem(review, reply, latestReport(ownerId, review));
    }

    private static String requireContent(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.length() > 1000) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Noi dung tra loi khong hop le.",
                    Map.of("content", "Cau tra loi tu 1 den 1000 ky tu"));
        }
        return text;
    }

    private com.courtly.domain.review.Report latestReport(UUID ownerId, VenueReview review) {
        return reportRepository.findByReporterAndTargets(ownerId, ReportTargetType.VENUE_REVIEW, List.of(review.getId()))
                .stream().findFirst().orElse(null);
    }

    static Item toItem(VenueReview review, VenueReviewReply reply, com.courtly.domain.review.Report report) {
        return new Item(review.getId(), review.getVenue().getId(), review.getVenue().getName(), review.getRating(),
                review.getComment(), review.getUser().getFullName(), review.getCreatedAt(),
                review.getUpdatedAt() != null && review.getUpdatedAt().isAfter(review.getCreatedAt().plusSeconds(1)),
                review.getStatus().getValue(),
                reply == null ? null : new Reply(reply.getContent(), reply.getCreatedAt(),
                        reply.getUpdatedAt() != null && reply.getUpdatedAt().isAfter(reply.getCreatedAt().plusSeconds(1))),
                report == null ? null : new com.courtly.api.owner.dto.OwnerVenueReviewResponses.Report(report.getId(), report.getReason(),
                        VenueReviewReportReasons.label(report.getReason()), report.getStatus().getValue(),
                        report.getHandlingAction(), report.getCreatedAt(), report.getHandledAt()));
    }
}
