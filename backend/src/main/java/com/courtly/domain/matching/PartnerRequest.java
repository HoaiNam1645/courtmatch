package com.courtly.domain.matching;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.PartnerRequestStatus;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Yeu cau ghep cap giua hai nguoi choi (2.2.31). */
@Entity
@Table(name = "partner_requests")
@Getter
@Setter
@NoArgsConstructor
public class PartnerRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(name = "message")
    private String message;

    @Column(name = "status", nullable = false, length = 30)
    private PartnerRequestStatus status = PartnerRequestStatus.PENDING;

    @Column(name = "responded_at")
    private Instant respondedAt;
}
