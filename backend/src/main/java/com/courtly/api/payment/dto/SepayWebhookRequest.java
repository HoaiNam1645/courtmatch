package com.courtly.api.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * Payload SePay gui ve khi tai khoan ngan hang co bien dong so du (2.1.37).
 *
 * <p>Chi khai bao cac truong duoc dung. {@code @JsonIgnoreProperties} de SePay them truong moi
 * khong lam webhook gay - gay o day nghia la mat tien cua nguoi dung.
 *
 * @param id             ma giao dich tren he thong SePay, dung lam khoa chong trung
 * @param gateway        ten ngan hang
 * @param transactionDate thoi diem giao dich, dinh dang chuoi cua SePay
 * @param accountNumber  so tai khoan nhan tien
 * @param transferType   {@code in} la tien vao, {@code out} la tien ra
 * @param transferAmount so tien
 * @param content        noi dung chuyen khoan, chua ma don
 * @param referenceCode  ma tham chieu cua ngan hang
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SepayWebhookRequest(String id,
                                  String gateway,
                                  String transactionDate,
                                  String accountNumber,
                                  String transferType,
                                  BigDecimal transferAmount,
                                  String content,
                                  String referenceCode) {
}
