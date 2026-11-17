package com.courtly.service.review;

import com.courtly.api.review.dto.MyVenueReviewResponse;
import com.courtly.api.review.dto.VenueReviewRequest;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.ReviewStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.review.VenueReview;
import com.courtly.domain.review.VenueReviewRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh gia san cua nguoi choi (2.3.77 - 2.3.79).
 *
 * <p>Chi nguoi co don da hoan thanh moi viet duoc, moi don mot danh gia, trong han {@code window-days} ngay sau khi hoan
 * thanh. Moi lan viet/sua/xoa deu tinh lai diem trung binh cua san tu cac danh gia dang hien thi.
 */
@Service
@RequiredArgsConstructor
public class VenueReviewService {

    static final int MAX_COMMENT = 1000;

    private final VenueReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    @Value("${courtly.reviews.window-days:7}")
    private int windowDays;

    /** 2.3.77 - viet danh gia cho mot don da hoan thanh cua chinh minh. */
    @Transactional
    public MyVenueReviewResponse create(UUID userId, UUID bookingId, VenueReviewRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .filter(item -> item.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay don dat san nay."));
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw ApiException.conflict(ErrorCode.VENUE_REVIEW_STATE_INVALID,
                    "Chi danh gia duoc don da hoan thanh.", java.util.Map.of());
        }
        if (reviewRepository.findByBookingAndUser(bookingId, userId).isPresent()) {
            throw ApiException.conflict(ErrorCode.VENUE_REVIEW_STATE_INVALID,
                    "Ban da danh gia don nay roi.", java.util.Map.of());
        }
        if (!Instant.now().isBefore(reviewDeadline(booking))) {
            throw ApiException.conflict(ErrorCode.VENUE_REVIEW_STATE_INVALID,
                    "Da qua " + windowDays + " ngay ke tu khi don hoan thanh, khong the danh gia nua.", java.util.Map.of());
        }

        VenueReview review = new VenueReview();
        review.setId(UUID.randomUUID());
        review.setVenue(booking.getVenue());
        review.setUser(booking.getUser());
        review.setBooking(booking);
        review.setRating(request.rating().shortValue());
        review.setComment(cleanComment(request.comment()));
        review.setStatus(ReviewStatus.VISIBLE);
        reviewRepository.saveAndFlush(review);
        reviewRepository.recalculateVenueRating(booking.getVenue().getId());
        return toResponse(review);
    }

    /** Han cuoi de viet danh gia cho don: moc hoan thanh + so ngay cho phep. */
    public Instant reviewDeadline(Booking booking) {
        return completedMoment(booking).plus(Duration.ofDays(windowDays));
    }

    /** Don co the viet danh gia ngay luc nay (chua danh gia, da hoan thanh, con han). */
    public boolean canReview(Booking booking, boolean alreadyReviewed, Instant now) {
        return booking.getStatus() == BookingStatus.COMPLETED && !alreadyReviewed && now.isBefore(reviewDeadline(booking));
    }

    private static Instant completedMoment(Booking booking) {
        if (booking.getCompletedAt() != null) return booking.getCompletedAt();
        return booking.getItems().stream().map(BookingItem::getEndTime).max(Comparator.naturalOrder())
                .orElse(booking.getCreatedAt());
    }

    static String cleanComment(String comment) {
        if (comment == null) return null;
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static MyVenueReviewResponse toResponse(VenueReview review) {
        return new MyVenueReviewResponse(review.getId(), review.getVenue().getId(),
                review.getBooking() == null ? null : review.getBooking().getId(), review.getRating(),
                review.getComment(), review.getStatus().getValue(), review.getCreatedAt(), review.getUpdatedAt());
    }
}
