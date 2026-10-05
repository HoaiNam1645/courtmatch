package com.courtly.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cau hinh JWT.
 *
 * @param secret            khoa ky HS256, toi thieu 32 ky tu; bat buoc doi o moi truong that
 * @param expirationMinutes thoi gian song cua access token
 * @param issuer            gia tri claim "iss"
 */
@ConfigurationProperties(prefix = "courtly.security.jwt")
public record JwtProperties(String secret, long expirationMinutes, String issuer) {
}
