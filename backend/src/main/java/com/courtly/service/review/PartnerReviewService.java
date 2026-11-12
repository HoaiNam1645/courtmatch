package com.courtly.service.review;

import com.courtly.api.review.dto.CreatePartnerReviewRequest;
import com.courtly.api.review.dto.PartnerReviewListResponse;
import com.courtly.api.review.dto.PartnerReviewResponse;
import com.courtly.common.enums.MatchStatus;
import com.courtly.common.enums.ReviewStatus;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.match.Match;
import com.courtly.domain.match.MatchPlayer;
import com.courtly.domain.match.MatchRepository;
import com.courtly.domain.review.PartnerReview;
import com.courtly.domain.review.PartnerReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PartnerReviewService {

    private final PartnerReviewRepository reviewRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;

    @Transactional
    public PartnerReviewResponse create(UUID reviewerId, CreatePartnerReviewRequest request) {
        User reviewer = requireActivePlayer(reviewerId);
        Match match = matchRepository.findById(request.matchId())
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tran dau."));
        if (match.getStatus() != MatchStatus.COMPLETED || match.getResult() == null
                || match.getResult().getConfirmedAt() == null) {
            throw ApiException.conflict(ErrorCode.MATCH_STATE_INVALID,
                    "Chi co the danh gia sau khi ket qua tran dau da duoc xac nhan.", Map.of());
        }

        MatchPlayer reviewerPlayer = participant(match, reviewerId);
        if (reviewerPlayer == null) {
            throw ApiException.forbidden(ErrorCode.PARTNER_REVIEW_NOT_ALLOWED,
                    "Chi nguoi tham gia tran dau moi duoc danh gia.");
        }
        MatchPlayer reviewedPlayer = participant(match, request.reviewedUserId());
        if (reviewedPlayer == null) {
            throw ApiException.badRequest(ErrorCode.PARTNER_REVIEW_NOT_ALLOWED,
                    "Nguoi duoc danh gia khong tham gia tran dau nay.",
                    Map.of("reviewedUserId", "Chon nguoi da tham gia tran dau"));
        }
        if (reviewerId.equals(request.reviewedUserId())) {
            throw ApiException.badRequest(ErrorCode.PARTNER_REVIEW_NOT_ALLOWED,
                    "Ban khong the tu danh gia chinh minh.",
                    Map.of("reviewedUserId", "Khong the tu danh gia"));
        }
        if (reviewRepository.existsByReviewerIdAndReviewedUserIdAndMatchId(
                reviewerId, request.reviewedUserId(), request.matchId())) {
            duplicateReview();
        }

        PartnerReview review = new PartnerReview();
        review.setReviewer(reviewer);
        review.setReviewedUser(reviewedPlayer.getUser());
        review.setMatch(match);
        review.setRating(request.rating().shortValue());
        review.setComment(normalizeComment(request.comment()));
        review.setStatus(ReviewStatus.VISIBLE);
        try {
            return toResponse(reviewRepository.saveAndFlush(review));
        } catch (DataIntegrityViolationException exception) {
            duplicateReview();
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PartnerReviewListResponse listVisible(UUID currentUserId, UUID playerId,
                                                 int page, int size) {
        requireActivePlayer(currentUserId);
        User player = userRepository.findById(playerId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay nguoi choi."));
        if (!userRepository.hasPlayerRole(player.getId())) {
            throw ApiException.notFound("Khong tim thay nguoi choi.");
        }
        Page<PartnerReview> reviews = reviewRepository
                .findByReviewedUserIdAndStatusOrderByCreatedAtDesc(
                        playerId, ReviewStatus.VISIBLE, PageRequest.of(page, size));
        long totalReviews = reviewRepository.countByReviewedUserIdAndStatus(
                playerId, ReviewStatus.VISIBLE);
        Double rawAverage = reviewRepository.averageRating(playerId, ReviewStatus.VISIBLE);
        BigDecimal average = rawAverage == null ? BigDecimal.ZERO
                : BigDecimal.valueOf(rawAverage).setScale(1, RoundingMode.HALF_UP);
        return new PartnerReviewListResponse(
                new PartnerReviewListResponse.Player(
                        player.getId(), player.getFullName(), player.getAvatarUrl()),
                average, totalReviews, reviews.getContent().stream().map(this::toResponse).toList(),
                reviews.getNumber(), reviews.getSize(), reviews.getTotalElements(),
                reviews.getTotalPages());
    }

    private User requireActivePlayer(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan."));
        if (user.getStatus() != UserStatus.ACTIVE || !userRepository.hasPlayerRole(userId)) {
            throw ApiException.forbidden(ErrorCode.FORBIDDEN, "Tai khoan khong hoat dong.");
        }
        return user;
    }

    private static MatchPlayer participant(Match match, UUID userId) {
        return match.getPlayers().stream()
                .filter(player -> player.getUser().getId().equals(userId))
                .findFirst().orElse(null);
    }

    private static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }

    private PartnerReviewResponse toResponse(PartnerReview review) {
        User reviewer = review.getReviewer();
        return new PartnerReviewResponse(
                review.getId(), review.getMatch() == null ? null : review.getMatch().getId(),
                review.getRating(), review.getComment(),
                new PartnerReviewResponse.Reviewer(
                        reviewer.getId(), reviewer.getFullName(), reviewer.getAvatarUrl()),
                review.getCreatedAt());
    }

    private static void duplicateReview() {
        throw ApiException.conflict(ErrorCode.PARTNER_REVIEW_DUPLICATE,
                "Ban da danh gia nguoi choi nay trong tran dau.", Map.of());
    }
}
