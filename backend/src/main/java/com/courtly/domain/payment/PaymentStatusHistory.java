package com.courtly.domain.payment;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.PaymentStatus;
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

/** Immutable audit trail for player reports and owner payment decisions. */
@Entity
@Table(name = "payment_status_history")
@Getter
@Setter
@NoArgsConstructor
public class PaymentStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "old_status", length = 40)
    private PaymentStatus oldStatus;

    @Column(name = "new_status", nullable = false, length = 40)
    private PaymentStatus newStatus;

    @Column(name = "reason")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;
}
