package com.courtly.domain.payment;

import com.courtly.common.AuditedEntity;
import com.courtly.common.enums.PaymentStatus;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Giao dich thanh toan cua mot booking (2.1.34). */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_owner_id", nullable = false)
    private User recipientOwner;

    @Column(name = "method", nullable = false, length = 30)
    private String method;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 40)
    private PaymentStatus status = PaymentStatus.AWAITING_PAYMENT;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "bank_account_no", length = 50)
    private String bankAccountNo;

    @Column(name = "bank_account_name", length = 150)
    private String bankAccountName;

    @Column(name = "reference_content", length = 255)
    private String referenceContent;

    @Column(name = "proof_url")
    private String proofUrl;

    @Column(name = "payer_reported_at")
    private Instant payerReportedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by")
    private User rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "expired_at")
    private Instant expiredAt;
}
