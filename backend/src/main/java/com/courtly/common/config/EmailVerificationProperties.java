package com.courtly.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nguong cho luong xac minh email khi dang ky (2.1.1).
 *
 * <p>Tach khoi {@link PasswordResetProperties} vi hai luong co the can nguong khac nhau:
 * nguoi vua dang ky dang cho ma nen de thoi gian song dai hon mot chut.
 *
 * @param codeTtlMinutes        ma xac minh song bao lau
 * @param maxAttempts           so lan nhap sai toi da cho mot ma
 * @param resendCooldownSeconds khoang cho toi thieu giua hai lan gui
 * @param maxSendsPerHour       so lan gui toi da trong mot gio cho cung mot dia chi
 */
@ConfigurationProperties(prefix = "courtly.auth.email-verification")
public record EmailVerificationProperties(int codeTtlMinutes,
                                          int maxAttempts,
                                          int resendCooldownSeconds,
                                          int maxSendsPerHour) {
}
