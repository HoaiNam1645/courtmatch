package com.courtly.service.mail;

/**
 * Da tao ma xac minh email cho tai khoan vua dang ky (2.1.1).
 *
 * <p>Ma goc chi ton tai trong su kien nay va trong email gui di; database chi luu hash.
 *
 * @param email      dia chi nhan ma
 * @param fullName   ten nguoi nhan, dung de xung ho trong email
 * @param code       ma 6 chu so
 * @param ttlMinutes so phut ma con hieu luc
 */
public record EmailVerificationCodeIssued(String email, String fullName, String code, int ttlMinutes) {
}
