package com.courtly.api.auth.dto;

/**
 * Ket qua cua buoc dang ky va cua nut "Gui lai ma" (2.1.1).
 *
 * <p>Khong tra access token: tai khoan chua xac minh thi chua duoc dang nhap.
 *
 * @param email              dia chi da gui ma, de man hinh tiep theo hien lai
 * @param expiresInSeconds   ma con hieu luc bao lau
 * @param resendAfterSeconds phai cho bao lau moi duoc bam gui lai
 */
public record VerificationSentResponse(String email, long expiresInSeconds, long resendAfterSeconds) {
}
