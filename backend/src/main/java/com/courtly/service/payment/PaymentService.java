package com.courtly.service.payment;

import com.courtly.api.payment.dto.PaymentDetailResponse;
import com.courtly.common.config.SepayProperties;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.PaymentStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giao dich thanh toan SePay: tao (2.1.34), hien thi QR (2.1.35, 2.1.36),
 * doc trang thai (2.1.39), thu lai (2.1.41), het han (2.1.42).
 *
 * <p>Viec xac nhan da nhan tien khong nam o day ma o {@link SepayWebhookService}: chi ngan hang
 * moi biet tien da ve, nguoi dung bam nut khong lam don thanh da thanh toan duoc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final SepayProperties sepay;

    /**
     * Canh bao som khi cau hinh con la gia tri demo.
     *
     * <p>Khong co khoa webhook thi moi webhook deu bi tu choi, nghia la khong don nao duoc xac
     * nhan - loi nay chi lo ra khi co nguoi that chuyen tien, qua muon.
     */
    @PostConstruct
    void warnIfNotConfigured() {
        if (sepay.apiKey() == null || sepay.apiKey().isBlank()) {
            log.warn("Chua co SEPAY_API_KEY: webhook SePay se bi tu choi, don se khong tu xac nhan duoc");
        }
    }

    /**
     * Tao giao dich cho mot booking vua duoc tao (2.1.33, 2.1.34).
     *
     * <p>Goi trong cung transaction voi luc tao booking: don va giao dich phai sinh ra cung nhau,
     * neu khong se co don treo khong bao gio tra duoc tien.
     *
     * @param booking don vua tao, da co ma don va han thanh toan
     * @return payment da gan vao booking, chua duoc flush
     */
    public Payment createFor(Booking booking) {
        requireConfigured();

        String transferContent = transferContentOf(booking.getBookingCode());

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setBooking(booking);
        payment.setProvider(Payment.PROVIDER_SEPAY);
        payment.setAmount(booking.getTotalAmount());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setBankAccountNo(sepay.accountNo());
        payment.setTransferContent(transferContent);
        payment.setQrUrl(buildQrUrl(booking.getTotalAmount(), transferContent));
        payment.setExpiredAt(booking.getExpiresAt());
        payment.setCreatedAt(booking.getCreatedAt());

        return paymentRepository.save(payment);
    }

    /** 2.1.34 - 2.1.36, 2.1.39: man hinh thanh toan va cac lan hoi lai trang thai. */
    @Transactional(readOnly = true)
    public PaymentDetailResponse getDetail(UUID userId, UUID paymentId) {
        Payment payment = paymentRepository.findDetailById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay giao dich thanh toan."));
        requireOwner(userId, payment);
        return toResponse(payment, Instant.now());
    }

    /** 2.1.40: man hinh dat san thanh cong doc giao dich theo don, khong can nho paymentId. */
    @Transactional(readOnly = true)
    public PaymentDetailResponse getByBooking(UUID userId, UUID bookingId) {
        Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)
                .orElseThrow(() -> ApiException.notFound("Don nay chua co giao dich thanh toan."));
        requireOwner(userId, payment);
        return toResponse(payment, Instant.now());
    }

    /**
     * 2.1.41 - thu thanh toan lai sau khi that bai hoac het han.
     *
     * <p>Mo lai cua so thanh toan tren chinh giao dich cu thay vi tao giao dich moi: noi dung
     * chuyen khoan giu nguyen nen neu nguoi dung da chuyen tien bang ma QR cu thi webhook van
     * doi chieu dung.
     *
     * <p>Chi mo lai duoc khi don van con giu cho. Don da bi job huy thi phai dat lai tu dau.
     */
    @Transactional
    public PaymentDetailResponse retry(UUID userId, UUID paymentId) {
        Payment payment = paymentRepository.findDetailById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay giao dich thanh toan."));
        requireOwner(userId, payment);

        if (payment.getStatus() != PaymentStatus.FAILED && payment.getStatus() != PaymentStatus.EXPIRED) {
            throw ApiException.conflict(ErrorCode.PAYMENT_NOT_RETRYABLE,
                    "Giao dich nay khong o trang thai can thu lai.",
                    Map.of("status", "Chi thu lai duoc giao dich that bai hoac het han"));
        }

        Booking booking = payment.getBooking();
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw ApiException.conflict(ErrorCode.PAYMENT_NOT_RETRYABLE,
                    "Don nay khong con giu cho nua, vui long chon lich moi.",
                    Map.of("bookingId", "Don da het hieu luc"));
        }

        Instant now = Instant.now();
        Instant newDeadline = now.plus(PAYMENT_WINDOW_MINUTES, ChronoUnit.MINUTES);

        payment.setStatus(PaymentStatus.PENDING);
        payment.setExpiredAt(newDeadline);
        // QR cu co the da sinh voi so tien khac neu bang gia doi, sinh lai cho chac.
        payment.setQrUrl(buildQrUrl(payment.getAmount(), payment.getTransferContent()));
        booking.setExpiresAt(newDeadline);

        log.info("Mo lai cua so thanh toan cho don {}", booking.getBookingCode());
        return toResponse(payment, now);
    }

    /**
     * 2.1.42 - dong cac giao dich qua han.
     *
     * <p>Chay rieng voi viec huy don: mot giao dich co the het han trong khi don van con duoc
     * giu them vai giay cho toi luot job huy don chay.
     *
     * @return so giao dich vua chuyen sang het han
     */
    @Transactional
    public int expireOverduePayments() {
        Instant now = Instant.now();
        List<Payment> overdue = paymentRepository.findExpiredPending(now);
        for (Payment payment : overdue) {
            payment.setStatus(PaymentStatus.EXPIRED);
        }
        if (!overdue.isEmpty()) {
            log.info("Dong {} giao dich qua han", overdue.size());
        }
        return overdue.size();
    }

    /** Thoi gian giu cho, trung voi cua so thanh toan cua booking. */
    private static final int PAYMENT_WINDOW_MINUTES = 10;

    /**
     * Noi dung chuyen khoan sinh tu ma don, bo dau gach.
     *
     * <p>Nhieu ngan hang cat ky tu dac biet trong noi dung chuyen khoan, nen chi giu chu va so.
     * Day la soi day duy nhat noi tien voi don (2.1.38).
     */
    static String transferContentOf(String bookingCode) {
        return bookingCode.replaceAll("[^A-Za-z0-9]", "");
    }

    private String buildQrUrl(BigDecimal amount, String transferContent) {
        return "%s?acc=%s&bank=%s&amount=%s&des=%s".formatted(
                sepay.qrBaseUrl(),
                encode(sepay.accountNo()),
                encode(sepay.bankCode()),
                amount.toBigInteger(),
                encode(transferContent));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Thieu cau hinh ma van tao don thi nguoi dung nhan duoc ma QR tro vao tai khoan rong. */
    private void requireConfigured() {
        if (isBlank(sepay.accountNo()) || isBlank(sepay.bankCode()) || isBlank(sepay.qrBaseUrl())) {
            throw ApiException.conflict(ErrorCode.PAYMENT_GATEWAY_NOT_CONFIGURED,
                    "Cong thanh toan chua duoc cau hinh, vui long thu lai sau.",
                    Map.of());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireOwner(UUID userId, Payment payment) {
        if (!payment.getBooking().getUser().getId().equals(userId)) {
            // Tra 404 chu khong phai 403: khong xac nhan giup nguoi la rang paymentId nay co that.
            throw ApiException.notFound("Khong tim thay giao dich thanh toan.");
        }
    }

    private PaymentDetailResponse toResponse(Payment payment, Instant now) {
        Booking booking = payment.getBooking();
        long secondsRemaining = 0;
        if (payment.getStatus() == PaymentStatus.PENDING && payment.getExpiredAt() != null) {
            secondsRemaining = Math.max(0, ChronoUnit.SECONDS.between(now, payment.getExpiredAt()));
        }
        return new PaymentDetailResponse(
                payment.getId(),
                booking.getId(),
                booking.getBookingCode(),
                payment.getProvider(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getQrUrl(),
                sepay.bankName(),
                payment.getBankAccountNo(),
                sepay.accountName(),
                payment.getTransferContent(),
                payment.getProviderTransactionId(),
                payment.getPaidAt(),
                payment.getExpiredAt(),
                secondsRemaining,
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
