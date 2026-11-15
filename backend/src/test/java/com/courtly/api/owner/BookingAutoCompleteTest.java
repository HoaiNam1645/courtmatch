package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.PaymentStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import com.courtly.service.booking.BookingService;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Van de.docx muc 5 - booking da choi xong van tinh hoa hong du chu san chua xac nhan thu tien. */
class BookingAutoCompleteTest extends OwnerApiTestBase {

    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private BookingCommissionRepository commissionRepository;

    private User owner;
    private String ownerToken;
    private String playerToken;
    private Court court;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-auto");
        ownerToken = loginAs(owner);
        Venue venue = persistVenue(owner, "San Tu Hoan Thanh");
        court = venue.getCourts().getFirst();
        playerToken = loginAs(persistUser("player-auto", Role.PLAYER));
    }

    @Test
    @DisplayName("Da bao thanh toan, qua han thanh toan va gio choi da xong: khong bi het han, tu hoan thanh va ghi hoa hong")
    void reportedBookingCompletesAndCreatesCommission() throws Exception {
        String bookingId = acceptAndReport();
        makePlayed(bookingId, true);

        bookingService.expireOverdueBookings();
        assertThat(booking(bookingId).getStatus()).isEqualTo(BookingStatus.AWAITING_PAYMENT);

        assertThat(bookingService.completeFinishedBookings()).isGreaterThanOrEqualTo(1);

        Booking booking = booking(bookingId);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(booking.getCompletedAt()).isNotNull();
        assertThat(booking.getPaymentExpiresAt()).isNull();
        assertThat(booking.getItems()).allMatch(item -> item.getStatus().getValue().equals("completed"));
        BookingCommission commission = commissionRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(commission.getStatus()).isEqualTo("due");
        assertThat(commission.getGrossAmount()).isEqualByComparingTo(booking.getTotalAmount());
        assertThat(commission.getCommissionAmount()).isPositive();
        assertThat(commission.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(booking.getStatusHistory()).anyMatch(step ->
                step.getNewStatus() == BookingStatus.COMPLETED && step.getOldStatus() == BookingStatus.AWAITING_PAYMENT
                        && step.getReason().contains("chua xac nhan thu tien"));

        // Thanh toan van la payer_reported nen chu san thay nhan "chua xac nhan thu tien".
        mockMvc.perform(get("/api/v1/owner/bookings").param("group", "all").param("q", booking.getBookingCode())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("completed"))
                .andExpect(jsonPath("$.content[0].paymentStatus").value("payer_reported"));
        // Hoa hong nam trong cong no cua chu san.
        mockMvc.perform(get("/api/v1/owner/commission-debt/bookings").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].bookingCode").value(booking.getBookingCode()))
                .andExpect(jsonPath("$.content[0].status").value("due"));
    }

    @Test
    @DisplayName("Chay lai nhieu lan chi ghi nhan dung mot hoa hong")
    void completionIsIdempotent() throws Exception {
        String bookingId = acceptAndReport();
        makePlayed(bookingId, true);
        bookingService.completeFinishedBookings();
        assertThat(bookingService.completeFinishedBookings()).isZero();
        assertThat(commissionRepository.findByBookingId(UUID.fromString(bookingId))).isPresent();
        assertThat(booking(bookingId).getStatusHistory().stream()
                .filter(step -> step.getNewStatus() == BookingStatus.COMPLETED).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Chua bao thanh toan: het han binh thuong, khong hoan thanh, khong co hoa hong")
    void unreportedBookingStillExpires() throws Exception {
        String bookingId = accept(createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0)));
        makePlayed(bookingId, true);

        bookingService.expireOverdueBookings();
        bookingService.completeFinishedBookings();

        assertThat(booking(bookingId).getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(commissionRepository.findByBookingId(UUID.fromString(bookingId))).isEmpty();
    }

    @Test
    @DisplayName("Chu san da tu choi bao cao thanh toan: khong tu hoan thanh va khong co hoa hong")
    void rejectedReportDoesNotComplete() throws Exception {
        String bookingId = acceptAndReport();
        String paymentId = paymentOf(bookingId).getId().toString();
        mockMvc.perform(post("/api/v1/owner/payments/" + paymentId + "/reject").header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Chua thay tien\"}"))
                .andExpect(status().isOk());
        makePlayed(bookingId, true);

        bookingService.expireOverdueBookings();
        bookingService.completeFinishedBookings();

        assertThat(booking(bookingId).getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(commissionRepository.findByBookingId(UUID.fromString(bookingId))).isEmpty();
    }

    @Test
    @DisplayName("Da bao thanh toan nhung gio choi chua ket thuc: giu nguyen cho chu san xu ly, chua co hoa hong")
    void notPlayedYetIsKept() throws Exception {
        String bookingId = acceptAndReport();
        makePlayed(bookingId, false);

        bookingService.expireOverdueBookings();
        bookingService.completeFinishedBookings();

        assertThat(booking(bookingId).getStatus()).isEqualTo(BookingStatus.AWAITING_PAYMENT);
        assertThat(commissionRepository.findByBookingId(UUID.fromString(bookingId))).isEmpty();
    }

    @Test
    @DisplayName("Chu san xac nhan thu tien sau khi booking da tu hoan thanh: thanh toan confirmed, booking van completed, hoa hong khong doi")
    void confirmAfterAutoCompletionKeepsBookingCompleted() throws Exception {
        String bookingId = acceptAndReport();
        makePlayed(bookingId, false);
        // Gio choi ket thuc truoc khi het han thanh toan: moc thanh toan van con hieu luc.
        Booking booking = booking(bookingId);
        booking.getItems().forEach(item -> {
            item.setStartTime(Instant.now().minus(3, ChronoUnit.HOURS));
            item.setEndTime(Instant.now().minus(1, ChronoUnit.HOURS));
        });
        bookingRepository.saveAndFlush(booking);
        bookingService.completeFinishedBookings();
        BookingCommission before = commissionRepository.findByBookingId(booking.getId()).orElseThrow();

        Payment payment = paymentOf(bookingId);
        assertThat(payment.getExpiredAt()).isAfter(Instant.now());
        mockMvc.perform(post("/api/v1/owner/payments/" + payment.getId() + "/confirm")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("confirmed"));

        assertThat(booking(bookingId).getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(paymentOf(bookingId).getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        BookingCommission after = commissionRepository.findByBookingId(booking.getId()).orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getCommissionAmount()).isEqualByComparingTo(before.getCommissionAmount());
    }

    @Test
    @DisplayName("Booking da xac nhan thu tien van hoan thanh nhu cu (khong hoi quy)")
    void confirmedBookingStillCompletes() throws Exception {
        String bookingId = acceptAndReport();
        Payment payment = paymentOf(bookingId);
        mockMvc.perform(post("/api/v1/owner/payments/" + payment.getId() + "/confirm")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        assertThat(booking(bookingId).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        makePlayed(bookingId, true);

        bookingService.completeFinishedBookings();

        assertThat(booking(bookingId).getStatus()).isEqualTo(BookingStatus.COMPLETED);
        assertThat(commissionRepository.findByBookingId(UUID.fromString(bookingId))).isPresent();
        assertThat(paymentOf(bookingId).getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    private String acceptAndReport() throws Exception {
        String bookingId = accept(createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0)));
        String paymentId = paymentOf(bookingId).getId().toString();
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/report-paid").header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"owner_bank_transfer\",\"proofUrl\":\"https://example.com/proof.png\"}"))
                .andExpect(status().isOk());
        return bookingId;
    }

    private String accept(String bookingId) throws Exception {
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + bookingId + "/accept").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        return bookingId;
    }

    private Booking booking(String bookingId) {
        bookingRepository.flush();
        return bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
    }

    private Payment paymentOf(String bookingId) {
        return paymentRepository.findAll().stream()
                .filter(item -> item.getBooking().getId().toString().equals(bookingId))
                .findFirst().orElseThrow();
    }

    /** Dua gio choi ve qua khu; expired=true thi han thanh toan cung da qua. */
    private void makePlayed(String bookingId, boolean paymentWindowExpired) {
        Booking booking = booking(bookingId);
        Instant start = Instant.now().minus(3, ChronoUnit.HOURS);
        if (paymentWindowExpired) {
            booking.getItems().forEach(item -> {
                item.setStartTime(start);
                item.setEndTime(start.plus(90, ChronoUnit.MINUTES));
            });
            booking.setPaymentExpiresAt(Instant.now().minus(30, ChronoUnit.MINUTES));
            Payment payment = paymentOf(bookingId);
            payment.setExpiredAt(booking.getPaymentExpiresAt());
            paymentRepository.save(payment);
        } else {
            booking.getItems().forEach(item -> {
                item.setStartTime(Instant.now().plus(2, ChronoUnit.DAYS));
                item.setEndTime(Instant.now().plus(2, ChronoUnit.DAYS).plus(90, ChronoUnit.MINUTES));
            });
        }
        bookingRepository.saveAndFlush(booking);
    }
}
