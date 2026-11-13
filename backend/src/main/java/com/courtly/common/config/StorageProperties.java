package com.courtly.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Noi luu anh san tai len (2.3.6).
 *
 * @param uploadDir     thu muc goc tren dia cua server
 * @param publicBaseUrl goc URL cong khai cua backend, vd https://courtmatch.tech
 */
@ConfigurationProperties(prefix = "courtly.storage")
public record StorageProperties(String uploadDir, String publicBaseUrl) {
}
