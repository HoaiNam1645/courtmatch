package com.courtly.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Danh sach origin duoc phep goi API, vi frontend chay o cong khac. */
@ConfigurationProperties(prefix = "courtly.security.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
