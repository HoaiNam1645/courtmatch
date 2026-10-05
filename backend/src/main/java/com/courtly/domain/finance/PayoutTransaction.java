package com.courtly.domain.finance;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.PayoutStatus;
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

/** Giao dich chi tra thuc te cho chu san sau khi duyet rut tien (2.3.49). */
@Entity
@Table(name = "payout_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PayoutTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "withdrawal_request_id", nullable = false)
    private WithdrawalRequest withdrawalRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;

    @Column(name = "status", nullable = false, length = 30)
    private PayoutStatus status = PayoutStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;
}
