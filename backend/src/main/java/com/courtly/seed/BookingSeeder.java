package com.courtly.seed;

import com.courtly.common.enums.BookingItemStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.PaymentStatus;
import com.courtly.domain.account.CourtOwnerProfile;
import com.courtly.domain.account.CourtOwnerProfileRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.booking.BookingStatusHistory;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.payment.PaymentStatusHistory;
import com.courtly.domain.payment.PaymentStatusHistoryRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Small direct-payment dataset for local booking screens. */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class BookingSeeder implements Seeder {

    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository paymentHistoryRepository;
    private final UserRepository userRepository;
    private final CourtRepository courtRepository;
    private final CourtOwnerProfileRepository ownerProfileRepository;

    @Override
    public String name() {
        return "bookings + direct payments";
    }

    @Override
    @Transactional
    public void seed() {
        if (bookingRepository.count() > 0) {
            log.info("  [bookings] da co du lieu, bo qua");
            return;
        }

        seedBooking("demo-owner-review", "CT-DEMO-0001", "player-01", "court-01-a",
                BookingStatus.PENDING_OWNER_CONFIRMATION, 2);
        Booking awaiting = seedBooking("demo-awaiting-payment", "CT-DEMO-0002", "player-02",
                "court-01-b", BookingStatus.AWAITING_PAYMENT, 3);
        seedPayment(awaiting);
        log.info("  [bookings] da tao 2 booking theo luong thanh toan truc tiep");
    }

    private Booking seedBooking(String key, String code, String playerKey, String courtKey,
                                BookingStatus status, int daysFromNow) {
        User player = userRepository.findById(SeedIds.of("user:" + playerKey)).orElseThrow();
        Court court = courtRepository.findById(SeedIds.of("court:" + courtKey)).orElseThrow();
        Instant now = Instant.now();
        Instant start = now.plus(daysFromNow, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        Booking booking = new Booking();
        booking.setId(SeedIds.of("booking:" + key));
        booking.setBookingCode(code);
        booking.setUser(player);
        booking.setVenue(court.getVenue());
        booking.setStatus(status);
        booking.setTotalAmount(BigDecimal.valueOf(150_000));
        booking.setPriceLockedAt(now);
        booking.setCreatedAt(now.minus(30, ChronoUnit.MINUTES));
        if (status == BookingStatus.PENDING_OWNER_CONFIRMATION) {
            booking.setOwnerResponseExpiresAt(now.plus(30, ChronoUnit.MINUTES));
        } else {
            User owner = court.getVenue().getOwner();
            booking.setOwnerRespondedBy(owner);
            booking.setOwnerRespondedAt(now.minus(10, ChronoUnit.MINUTES));
            booking.setPaymentExpiresAt(now.plus(30, ChronoUnit.MINUTES));
        }

        BookingItem item = new BookingItem();
        item.setId(SeedIds.of("booking-item:" + key));
        item.setBooking(booking);
        item.setCourt(court);
        item.setStartTime(start);
        item.setEndTime(start.plus(90, ChronoUnit.MINUTES));
        item.setPrice(booking.getTotalAmount());
        item.setStatus(BookingItemStatus.PENDING);
        item.setCreatedAt(booking.getCreatedAt());
        booking.getItems().add(item);

        BookingStatusHistory history = new BookingStatusHistory();
        history.setId(SeedIds.of("booking-history:" + key));
        history.setBooking(booking);
        history.setNewStatus(status);
        history.setReason(status == BookingStatus.PENDING_OWNER_CONFIRMATION
                ? "Da gui yeu cau cho chu san" : "Chu san da chap nhan, cho thanh toan");
        history.setChangedBy(status == BookingStatus.PENDING_OWNER_CONFIRMATION
                ? player : court.getVenue().getOwner());
        history.setCreatedAt(booking.getCreatedAt());
        booking.getStatusHistory().add(history);
        return bookingRepository.save(booking);
    }

    private void seedPayment(Booking booking) {
        User owner = booking.getVenue().getOwner();
        CourtOwnerProfile profile = ownerProfileRepository.findById(owner.getId()).orElseThrow();
        Payment payment = new Payment();
        payment.setId(SeedIds.of("payment:" + booking.getBookingCode()));
        payment.setBooking(booking);
        payment.setRecipientOwner(owner);
        payment.setMethod("owner_bank_transfer");
        payment.setAmount(booking.getTotalAmount());
        payment.setStatus(PaymentStatus.AWAITING_PAYMENT);
        payment.setBankName(profile.getBankName());
        payment.setBankAccountNo(profile.getBankAccountNo());
        payment.setBankAccountName(profile.getBankAccountName());
        payment.setReferenceContent(booking.getBookingCode().replace("-", ""));
        payment.setExpiredAt(booking.getPaymentExpiresAt());
        payment.setCreatedAt(booking.getOwnerRespondedAt());
        paymentRepository.save(payment);

        PaymentStatusHistory history = new PaymentStatusHistory();
        history.setId(SeedIds.of("payment-history:" + booking.getBookingCode()));
        history.setPayment(payment);
        history.setNewStatus(PaymentStatus.AWAITING_PAYMENT);
        history.setReason("Chu san da chap nhan booking");
        history.setChangedBy(owner);
        history.setCreatedAt(payment.getCreatedAt());
        paymentHistoryRepository.save(history);
    }
}
