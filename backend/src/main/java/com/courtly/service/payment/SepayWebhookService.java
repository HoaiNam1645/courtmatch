package com.courtly.service.payment;

import com.courtly.api.payment.dto.SepayWebhookAck;
import com.courtly.api.payment.dto.SepayWebhookRequest;
import com.courtly.common.config.SepayProperties;
import com.courtly.common.enums.PaymentStatus;
import com.courtly.common.enums.WebhookProcessStatus;
import com.courtly.domain.booking.Booking;
import com.courtly.service.booking.BookingService;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.payment.SepayWebhookLog;
import com.courtly.domain.payment.SepayWebhookLogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhan webhook SePay (2.1.37) va doi chieu de xac nhan don (2.1.38).
 *
 * <p>Nguyen tac: moi ket cuc deu duoc ghi vao {@code sepay_webhook_logs}, ke ca khi khong khop
 * don nao. Khong co nhat ky thi mot giao dich lac se khong bao gio tim lai duoc.
 *
 * <p>Chong xu ly trung dua vao UNIQUE tren {@code transaction_code} o database, khong dua vao
 * kiem tra truoc trong code: SePay co the gui lai hai lan gan nhu dong thoi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SepayWebhookService {

    private static final String TRANSFER_TYPE_IN = "in";

    private final BookingService bookingService;
    private final PaymentRepository paymentRepository;
    private final SepayWebhookLogRepository webhookLogRepository;
    private final SepayProperties sepay;

    /**
     * Xu ly mot webhook.
     *
     * <p>Luon tra ve ack thanh cong khi da ghi nhan duoc, ke ca khi bo qua. Tra loi that bai
     * se khien SePay gui lai mai mot giao dich khong bao gio khop.
     */
    @Transactional
    public SepayWebhookAck handle(SepayWebhookRequest request) {
        String transactionCode = request.id();
        if (transactionCode == null || transactionCode.isBlank()) {
            log.warn("Webhook SePay khong co ma giao dich, bo qua");
            return new SepayWebhookAck(true, "Thieu ma giao dich");
        }

        // Chi tien vao moi lam don thanh da thanh toan. Tien ra la giao dich chi, khong lien quan.
        if (!TRANSFER_TYPE_IN.equalsIgnoreCase(request.transferType())) {
            return record(request, null, WebhookProcessStatus.IGNORED, "Khong phai giao dich tien vao");
        }

        String content = normalize(request.content());
        Optional<Payment> matched = findPayment(content);

        if (matched.isEmpty()) {
            // Co the la tien nap tay, hoac don da het han truoc khi tien ve. Giu lai de doi soat.
            log.warn("Webhook SePay {} khong khop don nao, noi dung: {}", transactionCode, request.content());
            return record(request, null, WebhookProcessStatus.IGNORED, "Khong tim thay don cho thanh toan");
        }

        Payment payment = matched.get();

        // Chuyen thieu tien thi khong xac nhan don. Chuyen thua thi van nhan, phan du xu ly tay.
        if (request.transferAmount() == null
                || request.transferAmount().compareTo(payment.getAmount()) < 0) {
            log.warn("Webhook SePay {} so tien khong du: nhan {} can {}",
                    transactionCode, request.transferAmount(), payment.getAmount());
            return record(request, payment, WebhookProcessStatus.FAILED, "So tien chuyen khong du");
        }

        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(now);
        payment.setProviderTransactionId(transactionCode);

        Booking booking = payment.getBooking();
        bookingService.confirmPaid(booking, now);

        log.info("Da nhan tien cho don {} qua giao dich {}", booking.getBookingCode(), transactionCode);
        return record(request, payment, WebhookProcessStatus.PROCESSED, null);
    }

    /**
     * Khoa webhook: SePay gui kem header {@code Authorization: Apikey <key>}.
     *
     * <p>Chua cau hinh khoa thi tu choi het. Mo cua cho moi request la mo duong cho bat ky ai
     * tren Internet tu xac nhan don cua ho.
     */
    public boolean isAuthorized(String authorizationHeader) {
        String expected = sepay.apiKey();
        if (expected == null || expected.isBlank()) {
            log.error("Chua cau hinh courtly.payment.sepay.api-key, tu choi moi webhook");
            return false;
        }
        if (authorizationHeader == null) {
            return false;
        }
        String provided = authorizationHeader.strip();
        if (provided.regionMatches(true, 0, "Apikey ", 0, "Apikey ".length())) {
            provided = provided.substring("Apikey ".length()).strip();
        }
        // So sanh khong phu thuoc do dai thoi gian xu ly.
        return java.security.MessageDigest.isEqual(
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Tim don theo noi dung chuyen khoan (2.1.38).
     *
     * <p>Ngan hang thuong chen them chu vao noi dung (vi du "CT202610020001 chuyen tien"), nen
     * khong so sanh bang nhau ma tach tung tu roi tim.
     */
    private Optional<Payment> findPayment(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        for (String token : content.split("\\s+")) {
            if (token.length() < 6) {
                continue;
            }
            Optional<Payment> found =
                    paymentRepository.findByTransferContentAndStatus(token, PaymentStatus.PENDING);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * Ghi nhat ky va tra ack.
     *
     * <p>Neu {@code transaction_code} da ton tai thi UNIQUE o database se chan - nghia la webhook
     * nay da duoc xu ly truoc do, coi nhu thanh cong va khong lam gi them.
     */
    private SepayWebhookAck record(SepayWebhookRequest request,
                                   Payment payment,
                                   WebhookProcessStatus status,
                                   String errorMessage) {
        if (webhookLogRepository.existsByTransactionCode(request.id())) {
            log.info("Webhook SePay {} da duoc xu ly truoc do, bo qua", request.id());
            return new SepayWebhookAck(true, "Da xu ly truoc do");
        }

        SepayWebhookLog logEntry = new SepayWebhookLog();
        logEntry.setId(UUID.randomUUID());
        logEntry.setPayment(payment);
        logEntry.setTransactionCode(request.id());
        logEntry.setPayloadJson(toPayload(request));
        logEntry.setProcessedStatus(status);
        logEntry.setErrorMessage(errorMessage);
        logEntry.setReceivedAt(Instant.now());
        if (status != WebhookProcessStatus.RECEIVED) {
            logEntry.setProcessedAt(Instant.now());
        }

        try {
            webhookLogRepository.saveAndFlush(logEntry);
        } catch (DataIntegrityViolationException e) {
            // Hai ban sao cua cung mot webhook toi gan nhu dong thoi.
            log.info("Webhook SePay {} bi trung, da co ban ghi khac", request.id());
            return new SepayWebhookAck(true, "Da xu ly truoc do");
        }

        return new SepayWebhookAck(true, status == WebhookProcessStatus.PROCESSED
                ? "Da xac nhan thanh toan"
                : "Da ghi nhan");
    }

    private static Map<String, Object> toPayload(SepayWebhookRequest request) {
        // HashMap chu khong phai Map.of: Map.of nem NullPointerException khi gia tri null.
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", request.id());
        payload.put("gateway", request.gateway());
        payload.put("transactionDate", request.transactionDate());
        payload.put("accountNumber", request.accountNumber());
        payload.put("transferType", request.transferType());
        payload.put("transferAmount", Optional.ofNullable(request.transferAmount())
                .map(BigDecimal::toPlainString).orElse(null));
        payload.put("content", request.content());
        payload.put("referenceCode", request.referenceCode());
        return payload;
    }

    private static String normalize(String content) {
        return content == null ? null : content.trim();
    }
}
