package com.courtly.api.auth.dto;

/**
 * @param resetToken       token dung mot lan cho buoc dat mat khau moi
 * @param expiresInSeconds token con hieu luc bao lau
 */
public record PasswordResetVerifyResponse(String resetToken, long expiresInSeconds) {
}
