package com.courtly.api.auth.dto;

/**
 * Tra ve giong het nhau du tai khoan co ton tai hay khong, de khong lo danh sach email.
 *
 * @param expiresInSeconds     ma xac minh con hieu luc bao lau
 * @param resendAfterSeconds   phai cho bao lau moi duoc bam gui lai
 */
public record PasswordResetSendResponse(long expiresInSeconds, long resendAfterSeconds) {
}
