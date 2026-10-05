package com.courtly.api.payment.dto;

/**
 * Tra loi cho SePay (2.1.37).
 *
 * <p>Luon tra 200 kem {@code success = true} khi da ghi nhan duoc webhook, ke ca khi khong
 * doi chieu duoc voi don nao. Tra loi loi se khien SePay gui lai mai mot giao dich khong bao
 * gio khop duoc.
 *
 * @param success da ghi nhan hay chua
 * @param message mo ta ngan de doc trong nhat ky cua SePay
 */
public record SepayWebhookAck(boolean success, String message) {
}
