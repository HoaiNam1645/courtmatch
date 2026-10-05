package com.courtly.seed;

import com.courtly.common.enums.BookingItemStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.PaymentStatus;
import com.courtly.common.enums.RefundStatus;
import com.courtly.common.enums.WebhookProcessStatus;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.booking.BookingStatusHistory;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.payment.Refund;
import com.courtly.domain.payment.RefundRepository;
import com.courtly.domain.payment.SepayWebhookLog;
import com.courtly.domain.payment.SepayWebhookLogRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buoc 4: bookings, booking_items, booking_status_history, payments,
 * sepay_webhook_logs va refunds.
 *
 * <p>Gia tien lay san tu bang gia trong {@code seed/venues.json} va viet thang vao day,
 * seeder khong tu tinh lai. Cac khung gio duoc chon lech nhau tren cung mot san
 * de khong vi pham exclusion constraint {@code booking_items_no_overlap}.
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class BookingSeeder implements Seeder {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Cua so giu cho thanh toan, khop voi dem nguoc 10 phut tren giao dien (2.1.42). */
    private static final int PAYMENT_WINDOW_MINUTES = 10;

    private static final DateTimeFormatter CODE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String BANK_NAME = "MBBank";
    private static final String BANK_ACCOUNT_NO = "0359000111";

    private final BookingRepository bookingRepository;
    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final SepayWebhookLogRepository webhookLogRepository;
    private final RefundRepository refundRepository;

    /**
     * @param dayOffset so ngay lech so voi hom nay, so am la qua khu
     * @param amount    tong tien cua don, bang gia/gio nhan so gio theo court_price_rules
     */
    private record BookingSeed(String key, String code, String playerKey, String venueKey,
                               String courtKey, int dayOffset, int startHour, int minutes,
                               long amount, BookingStatus status, PaymentStatus paymentStatus,
                               String note) {
    }

    private static final List<BookingSeed> BOOKINGS = List.of(
            // Sap toi, da thanh toan: court-01-a khung 16:00-23:00 gia 140.000/gio x 1,5 gio.
            new BookingSeed("booking-01", "CT-20260924-0001", "player-01", "venue-01", "court-01-a",
                    1, 18, 90, 210_000, BookingStatus.CONFIRMED, PaymentStatus.PAID,
                    "Nho chuan bi 2 ong cau."),
            // Dang cho thanh toan: court-02-a khung 16:00-22:30 gia 130.000/gio x 1 gio.
            new BookingSeed("booking-02", "CT-20260924-0002", "player-02", "venue-02", "court-02-a",
                    1, 19, 60, 130_000, BookingStatus.PENDING_PAYMENT, PaymentStatus.PENDING, null),
            // Da choi xong: court-03-a khung 16:00-23:30 gia 140.000/gio x 1 gio.
            new BookingSeed("booking-03", "CT-20260913-0003", "player-03", "venue-03", "court-03-a",
                    -10, 20, 60, 140_000, BookingStatus.COMPLETED, PaymentStatus.PAID, null),
            // Da huy va hoan tien: court-04-b gia 150.000/gio x 1 gio.
            new BookingSeed("booking-04", "CT-20260918-0004", "player-04", "venue-04", "court-04-b",
                    -5, 17, 60, 150_000, BookingStatus.CANCELLED, PaymentStatus.REFUNDED,
                    "Ban dot xuat."),
            // Qua han thanh toan: court-05-a gia 110.000/gio x 1 gio.
            new BookingSeed("booking-05", "CT-20260921-0005", "player-05", "venue-05", "court-05-a",
                    -2, 18, 60, 110_000, BookingStatus.EXPIRED, PaymentStatus.EXPIRED, null),
            // Sap toi, da thanh toan: court-01-b khung 16:00-23:00 gia 140.000/gio x 1,5 gio.
            new BookingSeed("booking-06", "CT-20260925-0006", "player-06", "venue-01", "court-01-b",
                    2, 19, 90, 210_000, BookingStatus.CONFIRMED, PaymentStatus.PAID,
                    "Doi doi 4 nguoi."),
            // Da choi xong: court-06-a gia 95.000/gio x 1 gio.
            new BookingSeed("booking-07", "CT-20260903-0007", "player-07", "venue-06", "court-06-a",
                    -20, 21, 60, 95_000, BookingStatus.COMPLETED, PaymentStatus.PAID, null));

    @Override
    public String name() {
        return "bookings + payments";
    }

    @Override
    @Transactional
    public void seed() {
        if (bookingRepository.count() > 0) {
            log.info("  [bookings] da co du lieu, bo qua");
            return;
        }

        for (BookingSeed seed : BOOKINGS) {
            Venue venue = venueRepository.findById(SeedIds.of("venue:" + seed.venueKey())).orElseThrow();
            Court court = courtRepository.findById(SeedIds.of("court:" + seed.courtKey())).orElseThrow();
            User player = userRepository.findById(SeedIds.of("user:" + seed.playerKey())).orElseThrow();

            LocalDate date = LocalDate.now(ZONE).plusDays(seed.dayOffset());
            Instant start = LocalDateTime.of(date, LocalTime.of(seed.startHour(), 0))
                    .atZone(ZONE).toInstant();
            Instant end = start.plus(seed.minutes(), ChronoUnit.MINUTES);
            Instant createdAt = start.minus(2, ChronoUnit.DAYS);
            BigDecimal amount = BigDecimal.valueOf(seed.amount());

            Booking booking = new Booking();
            booking.setId(SeedIds.of("booking:" + seed.key()));
            booking.setBookingCode(seed.code());
            booking.setUser(player);
            booking.setVenue(venue);
            booking.setStatus(seed.status());
            booking.setTotalAmount(amount);
            booking.setNote(seed.note());
            booking.setCreatedAt(createdAt);
            booking.setExpiresAt(seed.status() == BookingStatus.PENDING_PAYMENT
                    // Don dang cho: tinh han tu bay gio de thu man hinh dem nguoc.
                    ? Instant.now().plus(PAYMENT_WINDOW_MINUTES, ChronoUnit.MINUTES)
                    : createdAt.plus(PAYMENT_WINDOW_MINUTES, ChronoUnit.MINUTES));

            if (seed.status() == BookingStatus.CONFIRMED
                    || seed.status() == BookingStatus.COMPLETED
                    || seed.status() == BookingStatus.CANCELLED) {
                booking.setConfirmedAt(createdAt.plus(4, ChronoUnit.MINUTES));
            }
            if (seed.status() == BookingStatus.CANCELLED) {
                booking.setCancelledAt(start.minus(6, ChronoUnit.HOURS));
                booking.setCancellationReason("Ban dot xuat, khong den duoc");
            }

            BookingItem item = new BookingItem();
            item.setId(SeedIds.of("booking-item:" + seed.key()));
            item.setBooking(booking);
            item.setCourt(court);
            item.setStartTime(start);
            item.setEndTime(end);
            item.setPrice(amount);
            item.setStatus(itemStatusFor(seed.status()));
            item.setCreatedAt(createdAt);
            booking.getItems().add(item);

            addStatusHistory(booking, seed, createdAt, player);
            bookingRepository.save(booking);

            seedPayment(booking, seed, start, createdAt);
        }

        log.info("  [bookings] {} don, {} thanh toan", BOOKINGS.size(), BOOKINGS.size());
    }

    private BookingItemStatus itemStatusFor(BookingStatus status) {
        return switch (status) {
            case CONFIRMED -> BookingItemStatus.CONFIRMED;
            case PENDING_PAYMENT -> BookingItemStatus.PENDING;
            case COMPLETED -> BookingItemStatus.COMPLETED;
            case EXPIRED -> BookingItemStatus.EXPIRED;
            case CANCELLED, REFUNDED -> BookingItemStatus.CANCELLED;
        };
    }

    /** Timeline trang thai cho man hinh chi tiet booking (2.1.30). */
    private void addStatusHistory(Booking booking, BookingSeed seed, Instant createdAt, User player) {
        record Step(BookingStatus from, BookingStatus to, String reason, boolean bySystem, int minuteOffset) {
        }

        List<Step> steps = switch (seed.status()) {
            case PENDING_PAYMENT -> List.of(
                    new Step(null, BookingStatus.PENDING_PAYMENT, "Tao don, cho thanh toan", false, 0));
            case CONFIRMED -> List.of(
                    new Step(null, BookingStatus.PENDING_PAYMENT, "Tao don, cho thanh toan", false, 0),
                    new Step(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED,
                            "Da nhan du tien qua SePay", true, 4));
            case COMPLETED -> List.of(
                    new Step(null, BookingStatus.PENDING_PAYMENT, "Tao don, cho thanh toan", false, 0),
                    new Step(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED,
                            "Da nhan du tien qua SePay", true, 4),
                    new Step(BookingStatus.CONFIRMED, BookingStatus.COMPLETED, "Da choi xong", true, 2880));
            case CANCELLED -> List.of(
                    new Step(null, BookingStatus.PENDING_PAYMENT, "Tao don, cho thanh toan", false, 0),
                    new Step(BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED,
                            "Da nhan du tien qua SePay", true, 4),
                    new Step(BookingStatus.CONFIRMED, BookingStatus.CANCELLED,
                            "Nguoi choi huy truoc gio choi", false, 1800));
            case EXPIRED -> List.of(
                    new Step(null, BookingStatus.PENDING_PAYMENT, "Tao don, cho thanh toan", false, 0),
                    new Step(BookingStatus.PENDING_PAYMENT, BookingStatus.EXPIRED,
                            "Qua han thanh toan " + PAYMENT_WINDOW_MINUTES + " phut", true,
                            PAYMENT_WINDOW_MINUTES));
            case REFUNDED -> List.of(
                    new Step(BookingStatus.CONFIRMED, BookingStatus.REFUNDED, "Da hoan tien", true, 0));
        };

        int index = 0;
        for (Step step : steps) {
            BookingStatusHistory history = new BookingStatusHistory();
            history.setId(SeedIds.of("booking-history:" + seed.key() + ":" + index++));
            history.setBooking(booking);
            history.setOldStatus(step.from());
            history.setNewStatus(step.to());
            history.setReason(step.reason());
            history.setChangedBy(step.bySystem() ? null : player);
            history.setCreatedAt(createdAt.plus(step.minuteOffset(), ChronoUnit.MINUTES));
            booking.getStatusHistory().add(history);
        }
    }

    private void seedPayment(Booking booking, BookingSeed seed, Instant start, Instant createdAt) {
        String transferContent = booking.getBookingCode().replace("-", "");

        Payment payment = new Payment();
        payment.setId(SeedIds.of("payment:" + seed.key()));
        payment.setBooking(booking);
        payment.setAmount(booking.getTotalAmount());
        payment.setStatus(seed.paymentStatus());
        payment.setBankAccountNo(BANK_ACCOUNT_NO);
        payment.setTransferContent(transferContent);
        payment.setQrUrl("https://qr.sepay.vn/img?acc=%s&bank=%s&amount=%s&des=%s".formatted(
                BANK_ACCOUNT_NO, BANK_NAME, booking.getTotalAmount().toBigInteger(), transferContent));
        payment.setCreatedAt(createdAt);
        payment.setExpiredAt(booking.getExpiresAt());

        boolean transferReceived = seed.paymentStatus() == PaymentStatus.PAID
                || seed.paymentStatus() == PaymentStatus.REFUNDED;
        if (transferReceived) {
            payment.setPaidAt(createdAt.plus(3, ChronoUnit.MINUTES));
            payment.setProviderTransactionId("SEPAY" + transferContent);
        }

        paymentRepository.save(payment);

        if (transferReceived) {
            seedWebhookLog(payment, booking, transferContent);
        }
        if (seed.paymentStatus() == PaymentStatus.REFUNDED) {
            seedRefund(payment, booking, start);
        }
    }

    /** Payload mo phong webhook SePay, transaction_code unique de chong xu ly trung (2.1.37). */
    private void seedWebhookLog(Payment payment, Booking booking, String transferContent) {
        SepayWebhookLog webhookLog = new SepayWebhookLog();
        webhookLog.setId(SeedIds.of("webhook-log:" + booking.getBookingCode()));
        webhookLog.setPayment(payment);
        webhookLog.setTransactionCode(payment.getProviderTransactionId());
        webhookLog.setPayloadJson(Map.of(
                "id", payment.getProviderTransactionId(),
                "gateway", BANK_NAME,
                "transactionDate", payment.getPaidAt().toString(),
                "accountNumber", BANK_ACCOUNT_NO,
                "transferType", "in",
                "transferAmount", payment.getAmount().toBigInteger(),
                "content", transferContent,
                "referenceCode", booking.getBookingCode()));
        webhookLog.setProcessedStatus(WebhookProcessStatus.PROCESSED);
        webhookLog.setReceivedAt(payment.getPaidAt());
        webhookLog.setProcessedAt(payment.getPaidAt().plusSeconds(2));
        webhookLogRepository.save(webhookLog);
    }

    private void seedRefund(Payment payment, Booking booking, Instant start) {
        Refund refund = new Refund();
        refund.setId(SeedIds.of("refund:" + booking.getBookingCode()));
        refund.setPayment(payment);
        refund.setBooking(booking);
        refund.setAmount(payment.getAmount());
        refund.setReason("Huy truoc gio choi tren 6 tieng, hoan 100%");
        refund.setStatus(RefundStatus.PROCESSED);
        refund.setRequestedBy(booking.getUser());
        refund.setProcessedBy(booking.getVenue().getOwner());
        refund.setCreatedAt(start.minus(6, ChronoUnit.HOURS));
        refund.setProcessedAt(start.minus(4, ChronoUnit.HOURS));
        refundRepository.save(refund);
    }
}
