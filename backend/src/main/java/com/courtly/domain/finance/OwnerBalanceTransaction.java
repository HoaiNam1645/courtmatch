package com.courtly.domain.finance;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.BalanceTransactionType;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.payment.Payment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * So cai bien dong so du chu san (2.3.20, 2.3.21).
 *
 * <p>So du hien tai la {@code balanceAfter} cua ban ghi moi nhat, khong luu rieng cot tong.
 */
@Entity
@Table(name = "owner_balance_transactions")
@Getter
@Setter
@NoArgsConstructor
public class OwnerBalanceTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "type", nullable = false, length = 30)
    private BalanceTransactionType type;

    /** Duong = cong tien, am = tru tien (phi nen tang, rut tien). */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "note")
    private String note;
}
