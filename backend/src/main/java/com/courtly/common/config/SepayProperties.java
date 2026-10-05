package com.courtly.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cau hinh cong thanh toan SePay (2.1.33 - 2.1.38).
 *
 * <p>Tai khoan nhan tien la tai khoan cua nen tang, khong phai cua chu san: tien ve nen tang
 * truoc, sau do moi doi soat va chi tra qua {@code owner_balances}.
 *
 * <p>{@code apiKey} de trong thi webhook bi tu choi het. Co y: tha chan nham con hon nhan
 * bua giao dich gia tu Internet.
 *
 * @param apiKey      khoa SePay gui kem header {@code Authorization: Apikey <key>}
 * @param bankCode    ma ngan hang theo chuan SePay/VietQR, dung de sinh anh QR
 * @param bankName    ten ngan hang hien thi cho nguoi dung
 * @param accountNo   so tai khoan nhan tien
 * @param accountName ten chu tai khoan hien thi cho nguoi dung
 * @param qrBaseUrl   dia chi sinh anh VietQR cua SePay
 */
@ConfigurationProperties(prefix = "courtly.payment.sepay")
public record SepayProperties(String apiKey,
                              String bankCode,
                              String bankName,
                              String accountNo,
                              String accountName,
                              String qrBaseUrl) {
}
