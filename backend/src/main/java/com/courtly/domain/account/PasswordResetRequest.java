package com.courtly.domain.account;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.ResetChannel;
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

/** Yeu cau quen mat khau: luu hash cua OTP/token, khong bao gio luu ma goc. */
@Entity
@Table(name = "password_reset_requests")
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "channel", nullable = false, length = 20)
    private ResetChannel channel;

    /** Email hoac so dien thoai nhan ma. */
    @Column(name = "destination", nullable = false, length = 255)
    private String destination;

    @Column(name = "verification_hash", nullable = false)
    private String verificationHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

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
