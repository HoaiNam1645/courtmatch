package com.courtly.domain.review;

import com.courtly.common.AuditedEntity;
import com.courtly.common.enums.ReviewStatus;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.venue.Venue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Danh gia san cua nguoi choi (2.1.23). */
@Entity
@Table(name = "venue_reviews", uniqueConstraints =
        @UniqueConstraint(name = "vr_unique", columnNames = {"venue_id", "user_id", "booking_id"}))
@Getter
@Setter
@NoArgsConstructor
public class VenueReview extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Gan voi booking cu the de xac nhan nguoi danh gia da thuc su choi o san. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    /** 1 den 5 sao. */
    @Column(name = "rating", nullable = false)
    private short rating;

    @Column(name = "comment")
    private String comment;

    @Column(name = "status", nullable = false, length = 30)
    private ReviewStatus status = ReviewStatus.VISIBLE;
}
