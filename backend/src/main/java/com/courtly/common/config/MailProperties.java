package com.courtly.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cau hinh gui email cua ung dung (khac {@code spring.mail.*} la cau hinh ket noi SMTP).
 *
 * @param enabled     false thi khong goi SMTP, chi ghi noi dung ra log - dung khi chay test
 *                    hoac khi may chua co file .env
 * @param fromAddress dia chi hien o o "Tu"
 * @param fromName    ten hien thi kem dia chi gui
 */
@ConfigurationProperties(prefix = "courtly.mail")
public record MailProperties(boolean enabled, String fromAddress, String fromName) {
}
