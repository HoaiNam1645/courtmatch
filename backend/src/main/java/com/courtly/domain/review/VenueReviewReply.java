package com.courtly.domain.review;

import com.courtly.common.AuditedEntity;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cau tra loi cong khai cua chu san duoi mot danh gia san (2.3.81). Moi danh gia toi da mot cau. */
@Entity
@Table(name = "venue_review_replies")
@Getter
@Setter
@NoArgsConstructor
public class VenueReviewReply extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private VenueReview review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "content", nullable = false)
    private String content;
}
