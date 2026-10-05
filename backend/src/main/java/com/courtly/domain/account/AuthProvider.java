package com.courtly.domain.account;

import com.courtly.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Danh tinh dang nhap ben ngoai (Google OAuth).
 *
 * <p>Du lieu Google khong ghi truc tiep vao {@link User} ma luu o day (quy tac 14 cua thiet ke).
 */
@Entity
@Table(name = "auth_providers", uniqueConstraints = {
        @UniqueConstraint(name = "auth_providers_provider_unique",
                columnNames = {"provider_name", "provider_user_id"}),
        @UniqueConstraint(name = "auth_providers_user_provider_unique",
                columnNames = {"user_id", "provider_name"})
})
@Getter
@Setter
@NoArgsConstructor
public class AuthProvider extends AuditedEntity {

    public static final String GOOGLE = "google";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider_name", nullable = false, length = 50)
    private String providerName;

    /** Truong "sub" cua Google. */
    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "provider_email", length = 255)
    private String providerEmail;

    @Column(name = "provider_email_verified", nullable = false)
    private boolean providerEmailVerified;

    @Column(name = "provider_avatar_url")
    private String providerAvatarUrl;

    /** Chi luu khi that su can goi API Google thay mat nguoi dung, va phai o dang da ma hoa. */
    @Column(name = "access_token_encrypted")
    private String accessTokenEncrypted;

    @Column(name = "refresh_token_encrypted")
    private String refreshTokenEncrypted;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @PrePersist
    void applyDefaults() {
        if (linkedAt == null) {
            linkedAt = Instant.now();
        }
    }
}
