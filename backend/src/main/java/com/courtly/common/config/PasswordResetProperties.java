package com.courtly.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nguong thoi gian va so lan cho luong quen mat khau (2.1.5).
 *
 * <p>Frontend hien thi lai cac gia tri nay cho nguoi dung (dem nguoc, so lan con lai)
 * nen doi o day la doi hop dong API.
 *
 * @param codeTtlMinutes        ma xac minh song bao lau
 * @param tokenTtlMinutes       sau khi xac minh dung ma, con bao lau de dat mat khau moi
 * @param maxAttempts           so lan nhap sai toi da cho mot ma, vuot qua thi ma bi huy
 * @param resendCooldownSeconds khoang cho toi thieu giua hai lan gui ma
 * @param maxSendsPerHour       so lan gui toi da trong mot gio cho cung mot dia chi
 */
@ConfigurationProperties(prefix = "courtly.auth.password-reset")
public record PasswordResetProperties(int codeTtlMinutes,
                                      int tokenTtlMinutes,
                                      int maxAttempts,
                                      int resendCooldownSeconds,
                                      int maxSendsPerHour) {
}
