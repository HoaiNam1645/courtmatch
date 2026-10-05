package com.courtly.domain.account;

import com.courtly.common.BaseEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Ma OTP xac minh email luc dang ky (2.1.1): luu hash, khong bao gio luu ma goc. */
@Entity
@Table(name = "email_verification_requests")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerificationRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Luu lai dia chi da gui, vi nguoi dung co the doi email sau do. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "verification_hash", nullable = false)
    private String verificationHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** Da dung xong hoac bi ma moi hon thay the. */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "attempt_count", nullable = false)
    private short attemptCount;

    @Column(name = "resend_count", nullable = false)
    private short resendCount;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "requested_ip")
    private String requestedIp;

    @Column(name = "user_agent")
    private String userAgent;
}
