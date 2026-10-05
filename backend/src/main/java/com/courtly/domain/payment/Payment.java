package com.courtly.domain.payment;

import com.courtly.common.AuditedEntity;
import com.courtly.common.enums.PaymentStatus;
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

/** Giao dich thanh toan cua mot booking (2.1.34). */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends AuditedEntity {

    public static final String PROVIDER_SEPAY = "sepay";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider = PROVIDER_SEPAY;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** URL anh VietQR do backend sinh, frontend hien thi truc tiep (2.1.35). */
    @Column(name = "qr_url")
    private String qrUrl;

    @Column(name = "bank_account_no", length = 50)
    private String bankAccountNo;

    /** Noi dung chuyen khoan, dung de doi soat voi webhook SePay (2.1.38). */
    @Column(name = "transfer_content", length = 255)
    private String transferContent;

    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "expired_at")
    private Instant expiredAt;
}
