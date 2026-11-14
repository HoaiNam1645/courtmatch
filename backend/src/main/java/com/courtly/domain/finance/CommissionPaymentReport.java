package com.courtly.domain.finance;

import com.courtly.common.AuditedEntity;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Owner's report that a commission payment was transferred to Courtly. */
@Entity
@Table(name = "commission_payment_reports")
@Getter
@Setter
@NoArgsConstructor
public class CommissionPaymentReport extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private CommissionSettlement settlement;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "paid_on", nullable = false)
    private LocalDate paidOn;

    @Column(name = "reference_code", nullable = false, length = 100)
    private String referenceCode;

    @Column(name = "proof_url")
    private String proofUrl;

    @Column(name = "note")
    private String note;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "pending_review";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_note")
    private String reviewNote;
}
