package com.courtly.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ket noi toi AI service (2.2.17).
 *
 * @param enabled          tat thi luon dung baseline
 * @param token            gui trong X-Internal-Token; trong = coi nhu AI khong kha dung
 * @param connectTimeoutMs timeout ket noi
 * @param timeoutMs        timeout doc ket qua (1.500 ms mac dinh); qua han thi dung baseline trong cung yeu cau
 * @param artifactDir      thu muc artifacts cua AI service, chi dung de doc metadata khi nhap model
 */
@ConfigurationProperties(prefix = "courtly.ai")
public record AiProperties(boolean enabled, String baseUrl, String token, int connectTimeoutMs, int timeoutMs, String artifactDir) {

    public boolean usable() {
        return enabled && token != null && !token.isBlank() && baseUrl != null && !baseUrl.isBlank();
    }
}
