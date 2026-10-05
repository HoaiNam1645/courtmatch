package com.courtly.service.mail;

/**
 * Da tao ma xac minh dat lai mat khau cho mot tai khoan (2.1.5).
 *
 * <p>Ma goc chi ton tai trong su kien nay va trong email gui di; database chi luu hash.
 *
 * @param email      dia chi nhan ma
 * @param fullName   ten nguoi nhan, dung de xung ho trong email
 * @param code       ma 6 chu so
 * @param ttlMinutes so phut ma con hieu luc
 */
public record PasswordResetCodeIssued(String email, String fullName, String code, int ttlMinutes) {
}
