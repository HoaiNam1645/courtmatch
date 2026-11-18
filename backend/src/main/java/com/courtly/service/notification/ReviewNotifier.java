package com.courtly.service.notification;

import com.courtly.domain.notification.NotificationType;
import com.courtly.domain.review.VenueReview;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Thong bao ve danh gia san (2.3.81). Goi ben trong transaction cua nghiep vu. */
@Component
@RequiredArgsConstructor
public class ReviewNotifier {

    private final NotificationService notifications;

    /** Quan tri vien an danh gia sau bao cao cua chu san: bao cho nguoi da viet danh gia. */
    public void hidden(VenueReview review) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reviewId", review.getId().toString());
        data.put("venueId", review.getVenue().getId().toString());
        notifications.send(review.getUser().getId(), NotificationType.VENUE_REVIEW_HIDDEN, "Đánh giá của bạn đã bị ẩn",
                "Đánh giá của bạn về %s đã bị quản trị viên ẩn vì nội dung không phù hợp với quy định cộng đồng."
                        .formatted(review.getVenue().getName()),
                "/bookings", data);
    }

    /** Chu san tra loi danh gia: bao cho nguoi da viet danh gia. */
    public void replied(VenueReview review) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reviewId", review.getId().toString());
        data.put("venueId", review.getVenue().getId().toString());
        notifications.send(review.getUser().getId(), NotificationType.VENUE_REVIEW_REPLIED, "Chủ sân đã trả lời đánh giá của bạn",
                "%s đã phản hồi đánh giá của bạn. Xem câu trả lời ở mục đánh giá của sân.".formatted(review.getVenue().getName()),
                "/san/" + review.getVenue().getSlug() + "#danh-gia", data);
    }
}
