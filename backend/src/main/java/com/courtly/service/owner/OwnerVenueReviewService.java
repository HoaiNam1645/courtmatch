package com.courtly.service.owner;

import com.courtly.api.owner.dto.OwnerVenueReviewResponses.Item;
import com.courtly.api.owner.dto.OwnerVenueReviewResponses.ListResponse;
import com.courtly.api.owner.dto.OwnerVenueReviewResponses.Reply;
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

    private static final Set<String> FILTERS = Set.of("all", "answered", "unanswered");

    private final VenueRepository venueRepository;
    private final OwnedVenueLookup ownedVenueLookup;
    private final VenueReviewRepository reviewRepository;
    private final VenueReviewReplyRepository replyRepository;
    private final UserRepository userRepository;
    private final ReviewNotifier reviewNotifier;

    /** Danh gia hien thi cua cac san cua chu san; {@code venueId} null = moi san. */
    @Transactional(readOnly = true)
    public ListResponse list(UUID ownerId, UUID venueId, String filter, Integer rating, int page, int size) {
        String normalized = filter == null || filter.isBlank() ? "all" : filter;
        if (!FILTERS.contains(normalized)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Bo loc khong hop le.",
                    Map.of("filter", "Chi nhan all, answered hoac unanswered"));
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

        Page<VenueReview> reviews = reviewRepository.findForOwner(venueIds, ReviewStatus.VISIBLE,
                rating == null ? 0 : rating, normalized, PageRequest.of(page, size));
        Map<UUID, VenueReviewReply> replies = reviews.isEmpty() ? Map.of()
                : replyRepository.findByReviewIds(reviews.getContent().stream().map(VenueReview::getId).toList()).stream()
                .collect(Collectors.toMap(reply -> reply.getReview().getId(), reply -> reply));
        long unanswered = reviewRepository.countUnanswered(ownedIds, ReviewStatus.VISIBLE);
        return new ListResponse(reviews.getContent().stream().map(review -> toItem(review, replies.get(review.getId()))).toList(),
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
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.length() > 1000) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Noi dung tra loi khong hop le.",
                    Map.of("content", "Cau tra loi tu 1 den 1000 ky tu"));
        }
        VenueReviewReply reply = new VenueReviewReply();
        reply.setId(UUID.randomUUID());
        reply.setReview(review);
        reply.setOwner(userRepository.getReferenceById(ownerId));
        reply.setContent(text);
        replyRepository.saveAndFlush(reply);
        reviewNotifier.replied(review);
        return toItem(review, reply);
    }

    private static Item toItem(VenueReview review, VenueReviewReply reply) {
        return new Item(review.getId(), review.getVenue().getId(), review.getVenue().getName(), review.getRating(),
                review.getComment(), review.getUser().getFullName(), review.getCreatedAt(),
                review.getUpdatedAt() != null && review.getUpdatedAt().isAfter(review.getCreatedAt().plusSeconds(1)),
                reply == null ? null : new Reply(reply.getContent(), reply.getCreatedAt(),
                        reply.getUpdatedAt() != null && reply.getUpdatedAt().isAfter(reply.getCreatedAt().plusSeconds(1))));
    }
}
