package com.courtly.seed;

import com.courtly.common.enums.ReportStatus;
import com.courtly.common.enums.ReportTargetType;
import com.courtly.common.enums.ReviewStatus;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.match.MatchRepository;
import com.courtly.domain.review.PartnerReview;
import com.courtly.domain.review.PartnerReviewRepository;
import com.courtly.domain.review.Report;
import com.courtly.domain.review.ReportRepository;
import com.courtly.domain.review.VenueReview;
import com.courtly.domain.review.VenueReviewRepository;
import com.courtly.domain.venue.VenueRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Buoc 8: venue_reviews, partner_reviews, reports. */
@Slf4j
@Component
@Order(8)
@RequiredArgsConstructor
public class ReviewSeeder implements Seeder {

    private final VenueReviewRepository venueReviewRepository;
    private final PartnerReviewRepository partnerReviewRepository;
    private final ReportRepository reportRepository;
    private final VenueRepository venueRepository;
    private final BookingRepository bookingRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;

    /** @param bookingKey null khi danh gia khong gan voi don dat san cu the */
    private record VenueReviewSeed(String key, String venueKey, String userKey, String bookingKey,
                                   int rating, int daysAgo, ReviewStatus status, String comment) {
    }

    private static final List<VenueReviewSeed> VENUE_REVIEWS = List.of(
            new VenueReviewSeed("venue-review-01", "venue-03", "player-03", "booking-03",
                    5, 9, ReviewStatus.VISIBLE, "San sach, den sang, chu san than thien."),
            new VenueReviewSeed("venue-review-02", "venue-06", "player-07", "booking-07",
                    4, 19, ReviewStatus.VISIBLE, "Gia tot nhung bai xe hoi chat vao gio cao diem."),
            new VenueReviewSeed("venue-review-03", "venue-01", "player-01", null,
                    5, 25, ReviewStatus.VISIBLE, "San VIP mat go rat dep, dat lich de."),
            new VenueReviewSeed("venue-review-04", "venue-04", "player-04", null,
                    3, 6, ReviewStatus.VISIBLE, "San ok nhung hom do dieu hoa khong mat."),
            new VenueReviewSeed("venue-review-05", "venue-02", "player-02", null,
                    2, 4, ReviewStatus.REPORTED, "Nhan vien tra loi khong lich su."));

    private record PartnerReviewSeed(String key, String reviewerKey, String reviewedKey,
                                     String matchKey, int rating, int daysAgo, String comment) {
    }

    private static final List<PartnerReviewSeed> PARTNER_REVIEWS = List.of(
            new PartnerReviewSeed("partner-review-01", "player-01", "player-05", "match-01",
                    5, 19, "Ban danh chac tay, phoi hop tot o khu vuc luoi."),
            new PartnerReviewSeed("partner-review-02", "player-05", "player-01", "match-01",
                    4, 19, "Cu tan cong tot, can giu suc o set 3."),
            new PartnerReviewSeed("partner-review-03", "player-06", "player-03", "match-06",
                    5, 2, "Trinh do cao, huong dan them nhieu cho toi."),
            new PartnerReviewSeed("partner-review-04", "player-08", "player-01", "match-06",
                    3, 2, "Danh tot nhung it giao tiep trong tran."));

    @Override
    public String name() {
        return "reviews + reports";
    }

    @Override
    @Transactional
    public void seed() {
        if (venueReviewRepository.count() > 0) {
            log.info("  [reviews] da co du lieu, bo qua");
            return;
        }

        VENUE_REVIEWS.forEach(this::seedVenueReview);
        PARTNER_REVIEWS.forEach(this::seedPartnerReview);
        seedReports();

        log.info("  [reviews] {} danh gia san, {} danh gia doi tac, 2 bao cao",
                VENUE_REVIEWS.size(), PARTNER_REVIEWS.size());
    }

    private void seedVenueReview(VenueReviewSeed seed) {
        Instant createdAt = Instant.now().minus(seed.daysAgo(), ChronoUnit.DAYS);

        VenueReview review = new VenueReview();
        review.setId(SeedIds.of("venue-review:" + seed.key()));
        review.setVenue(venueRepository.findById(SeedIds.of("venue:" + seed.venueKey())).orElseThrow());
        review.setUser(user(seed.userKey()));
        review.setBooking(seed.bookingKey() == null ? null
                : bookingRepository.findById(SeedIds.of("booking:" + seed.bookingKey())).orElseThrow());
        review.setRating((short) seed.rating());
        review.setComment(seed.comment());
        review.setStatus(seed.status());
        review.setCreatedAt(createdAt);
        review.setUpdatedAt(createdAt);
        venueReviewRepository.save(review);
    }

    private void seedPartnerReview(PartnerReviewSeed seed) {
        PartnerReview review = new PartnerReview();
        review.setId(SeedIds.of("partner-review:" + seed.key()));
        review.setReviewer(user(seed.reviewerKey()));
        review.setReviewedUser(user(seed.reviewedKey()));
        review.setMatch(matchRepository.findById(SeedIds.of("match:" + seed.matchKey())).orElseThrow());
        review.setRating((short) seed.rating());
        review.setComment(seed.comment());
        review.setStatus(ReviewStatus.VISIBLE);
        review.setCreatedAt(Instant.now().minus(seed.daysAgo(), ChronoUnit.DAYS));
        partnerReviewRepository.save(review);
    }

    /** Mot bao cao dang cho xu ly va mot bao cao da xu ly xong (2.3.36, 2.3.37). */
    private void seedReports() {
        VenueReview reportedReview = venueReviewRepository
                .findById(SeedIds.of("venue-review:venue-review-05")).orElseThrow();

        Report pending = new Report();
        pending.setId(SeedIds.of("report:report-01"));
        pending.setReporter(user("owner-01"));
        pending.setTargetType(ReportTargetType.VENUE_REVIEW);
        pending.setTargetId(reportedReview.getId());
        pending.setReason("false_information");
        pending.setDescription("Danh gia khong dung su that, nguoi viet chua tung den san.");
        pending.setStatus(ReportStatus.PENDING);
        pending.setCreatedAt(Instant.now().minus(3, ChronoUnit.DAYS));
        reportRepository.save(pending);

        Instant reportedAt = Instant.now().minus(30, ChronoUnit.DAYS);

        Report resolved = new Report();
        resolved.setId(SeedIds.of("report:report-02"));
        resolved.setReporter(user("player-02"));
        resolved.setTargetType(ReportTargetType.USER);
        resolved.setTargetId(user("player-08").getId());
        resolved.setReason("no_show");
        resolved.setDescription("Hen danh doi nhung khong den va khong bao truoc.");
        resolved.setStatus(ReportStatus.RESOLVED);
        resolved.setHandledBy(user("admin"));
        resolved.setHandledAt(reportedAt.plus(2, ChronoUnit.DAYS));
        resolved.setCreatedAt(reportedAt);
        reportRepository.save(resolved);
    }

    private User user(String key) {
        return userRepository.findById(SeedIds.of("user:" + key))
                .orElseThrow(() -> new IllegalStateException("Thieu tai khoan '" + key + "'"));
    }
}
