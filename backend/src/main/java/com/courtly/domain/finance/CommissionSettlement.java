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

/** Periodic commission reconciliation between the single owner and Courtly. */
@Entity
@Table(name = "commission_settlements")
@Getter
@Setter
@NoArgsConstructor
public class CommissionSettlement extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "opening_outstanding", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingOutstanding = BigDecimal.ZERO;

    @Column(name = "commission_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionTotal = BigDecimal.ZERO;

    @Column(name = "adjustment_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal adjustmentTotal = BigDecimal.ZERO;

    @Column(name = "amount_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountDue = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "open";

    @Column(name = "owner_submitted_at")
    private Instant ownerSubmittedAt;

    @Column(name = "payment_reference", length = 255)
    private String paymentReference;

    @Column(name = "proof_url")
    private String proofUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "note")
    private String note;
}
