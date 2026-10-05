package com.courtly.api.auth.dto;

/**
 * Ket qua dang ky / dang nhap.
 *
 * @param expiresIn so giay con lai truoc khi access token het han
 */
public record AuthResponse(String accessToken,
                           String tokenType,
                           long expiresIn,
                           UserResponse user) {

    public static AuthResponse of(String accessToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}
