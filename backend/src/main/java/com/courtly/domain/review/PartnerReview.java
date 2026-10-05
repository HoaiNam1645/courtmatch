package com.courtly.domain.review;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.ReviewStatus;
import com.courtly.domain.account.User;
import com.courtly.domain.match.Match;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Danh gia doi tac choi sau tran (2.2.43). */
@Entity
@Table(name = "partner_reviews")
@Getter
@Setter
@NoArgsConstructor
public class PartnerReview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewed_user_id", nullable = false)
    private User reviewedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    /** 1 den 5 sao. */
    @Column(name = "rating", nullable = false)
    private short rating;

    @Column(name = "comment")
    private String comment;

    @Column(name = "status", nullable = false, length = 30)
    private ReviewStatus status = ReviewStatus.VISIBLE;
}
