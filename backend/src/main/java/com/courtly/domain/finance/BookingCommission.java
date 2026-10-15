package com.courtly.domain.finance;

import com.courtly.common.AuditedEntity;
import com.courtly.common.enums.FeeType;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
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

/** Commission snapshot for one completed booking; also the source of owner debt. */
@Entity
@Table(name = "booking_commissions")
@Getter
@Setter
@NoArgsConstructor
public class BookingCommission extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_config_id")
    private PlatformFeeConfig feeConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settlement_id")
    private CommissionSettlement settlement;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "fee_type", nullable = false, length = 30)
    private FeeType feeType;

    @Column(name = "fee_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeValue;

    @Column(name = "commission_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal commissionAmount;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "pending";

    @Column(name = "calculated_at", nullable = false)
    private Instant calculatedAt;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "settled_at")
    private Instant settledAt;
}
