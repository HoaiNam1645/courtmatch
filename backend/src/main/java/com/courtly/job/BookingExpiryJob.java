package com.courtly.job;

import com.courtly.service.booking.BookingService;
import com.courtly.service.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tu dong huy don qua han thanh toan (2.1.42).
 *
 * <p>Khong co job nay thi don bo do se giu khung gio vinh vien va nguoi khac khong dat duoc.
 *
 * <p>Chu ky mot phut nghia la khung gio co the bi giu them toi da mot phut sau khi het han.
 * Chap nhan duoc voi cua so thanh toan 10 phut.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "courtly.jobs.booking-expiry", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class BookingExpiryJob {

    private final BookingService bookingService;
    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${courtly.jobs.booking-expiry.interval-ms:60000}")
    public void expireOverdueBookings() {
        try {
            // Dong giao dich truoc roi moi huy don: nguoi dung dang mo man hinh thanh toan se
            // thay trang thai "het han" thay vi thay don bien mat ma khong hieu vi sao.
            paymentService.expireOverduePayments();
            bookingService.expireOverdueBookings();
        } catch (Exception e) {
            // Job that bai mot lan khong duoc lam dung ca lich chay.
            log.error("Khong huy duoc don qua han", e);
        }
    }
}
