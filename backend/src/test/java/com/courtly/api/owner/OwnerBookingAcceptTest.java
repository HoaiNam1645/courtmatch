package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingItemStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 2.3.13 - Xac nhan yeu cau dat san. */
class OwnerBookingAcceptTest extends OwnerApiTestBase {

    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private User owner;
    private String ownerToken;
    private Court court;
    private String playerToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-accept");
        ownerToken = loginAs(owner);
        Venue venue = persistVenue(owner, "San Xac Nhan");
        court = venue.getCourts().getFirst();
        playerToken = loginAs(persistUser("player", Role.PLAYER));
    }

    @Test
    @DisplayName("Xac nhan: sang awaiting_payment, ghi nguoi xac nhan + thoi diem + han thanh toan, tao huong dan thanh toan, ghi audit")
    void acceptRequest() throws Exception {
        String id = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));

        mockMvc.perform(post(path(id)).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(id))
                .andExpect(jsonPath("$.status").value("awaiting_payment"))
                .andExpect(jsonPath("$.ownerRespondedByName").value(owner.getFullName()))
                .andExpect(jsonPath("$.ownerRespondedAt").exists())
                .andExpect(jsonPath("$.paymentExpiresAt").exists())
                .andExpect(jsonPath("$.respondBy").doesNotExist());

        Booking saved = bookingRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.AWAITING_PAYMENT);
        assertThat(saved.getOwnerRespondedBy().getId()).isEqualTo(owner.getId());
        assertThat(saved.getPaymentExpiresAt()).isAfter(Instant.now());
        // Khung gio van duoc giu cho den khi thanh toan.
        assertThat(saved.getItems()).allMatch(item -> item.getStatus() == BookingItemStatus.PENDING);
        assertThat(saved.getStatusHistory()).anySatisfy(history -> {
            assertThat(history.getNewStatus()).isEqualTo(BookingStatus.AWAITING_PAYMENT);
            assertThat(history.getChangedBy().getId()).isEqualTo(owner.getId());
        });

        Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(UUID.fromString(id)).orElseThrow();
        assertThat(payment.getBankAccountNo()).isEqualTo("0359111001");
        assertThat(payment.getAmount()).isEqualByComparingTo("180000");
        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("booking.accept");
            assertThat(log.getEntityId()).isEqualTo(UUID.fromString(id));
        });

        // Nguoi choi thay duoc trang thai moi.
        mockMvc.perform(get("/api/v1/bookings/" + id).header("Authorization", bearer(playerToken)))
                .andExpect(jsonPath("$.status").value("awaiting_payment"));
        // Khong con trong danh sach cho duyet.
        mockMvc.perform(get("/api/v1/owner/booking-requests").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content[?(@.bookingId=='" + id + "')]").isEmpty());
    }

    @Test
    @DisplayName("Xac nhan lan hai, don da bi huy / da qua han: 409; don cua chu san khac / id la: 404; id sai: 400")
    void invalidStates() throws Exception {
        String id = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        mockMvc.perform(post(path(id)).header("Authorization", bearer(ownerToken))).andExpect(status().isOk());
        mockMvc.perform(post(path(id)).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_REVIEW_INVALID"));

        String late = createBooking(playerToken, court, nextMonday(), LocalTime.of(12, 0));
        Booking lateBooking = bookingRepository.findById(UUID.fromString(late)).orElseThrow();
        lateBooking.setOwnerResponseExpiresAt(Instant.now().minusSeconds(30));
        bookingRepository.saveAndFlush(lateBooking);
        mockMvc.perform(post(path(late)).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("qua han")));
        assertThat(bookingRepository.findById(UUID.fromString(late)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_OWNER_CONFIRMATION);

        String cancelled = createBooking(playerToken, court, nextMonday(), LocalTime.of(15, 0));
        mockMvc.perform(post("/api/v1/bookings/" + cancelled + "/cancel").header("Authorization", bearer(playerToken))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"reason\":\"Doi lich\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(path(cancelled)).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isConflict());

        mockMvc.perform(post(path(UUID.randomUUID().toString())).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(path("khong-phai-uuid")).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Chu san khac khong xac nhan duoc; nguoi choi bi chan; chua dang nhap 401")
    void ownership() throws Exception {
        String id = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        String otherToken = loginAs(persistOwner("other"));
        mockMvc.perform(post(path(id)).header("Authorization", bearer(otherToken))).andExpect(status().isNotFound());
        mockMvc.perform(post(path(id)).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(post(path(id))).andExpect(status().isUnauthorized());
        assertThat(bookingRepository.findById(UUID.fromString(id)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_OWNER_CONFIRMATION);
    }

    @Test
    @DisplayName("Chu san con mat khau tam khong xac nhan duoc")
    void passwordChangeRequired() throws Exception {
        String id = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        owner.setPasswordChangeRequired(true);
        userRepository.saveAndFlush(owner);
        mockMvc.perform(post(path(id)).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    private static String path(String bookingId) {
        return "/api/v1/owner/booking-requests/" + bookingId + "/accept";
    }
}
