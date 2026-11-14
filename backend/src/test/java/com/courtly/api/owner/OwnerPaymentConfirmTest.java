package com.courtly.api.owner;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.27 - Xem chi tiet va xac nhan / tu choi thanh toan truc tiep cua booking. */
class OwnerPaymentConfirmTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/owner/payments/";

    @Autowired private PaymentRepository paymentRepository;

    private User owner;
    private String ownerToken;
    private Venue venue;
    private Court court;
    private String playerToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-confirm");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Xac Nhan");
        court = venue.getCourts().getFirst();
        User player = persistUser("player", Role.PLAYER);
        player.setPhone("0911000555");
        userRepository.saveAndFlush(player);
        playerToken = loginAs(player);
    }

    @Test
    @DisplayName("Chi tiet khi cho thanh toan: day du thong tin booking, tai khoan nhan, tien trinh; chua xac nhan / tu choi duoc")
    void detailAwaitingPayment() throws Exception {
        String bookingId = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        String paymentId = accept(bookingId);

        mockMvc.perform(get(PATH + paymentId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.status").value("awaiting_payment"))
                .andExpect(jsonPath("$.amount").value(180000))
                .andExpect(jsonPath("$.canConfirm").value(false))
                .andExpect(jsonPath("$.canReject").value(false))
                .andExpect(jsonPath("$.expired").value(false))
                .andExpect(jsonPath("$.booking.id").value(bookingId))
                .andExpect(jsonPath("$.booking.venueName").value("San Xac Nhan"))
                .andExpect(jsonPath("$.booking.playerPhone").value("0911000555"))
                .andExpect(jsonPath("$.booking.slots", hasSize(1)))
                .andExpect(jsonPath("$.timeline", hasSize(1)))
                .andExpect(jsonPath("$.timeline[0].newStatus").value("awaiting_payment"));
        confirm(paymentId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PAYMENT_STATUS_INVALID"));
        reject(paymentId, "Chua nhan duoc tien").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Nguoi choi bao da tra: hien bang chung, co the xac nhan / tu choi")
    void detailPayerReported() throws Exception {
        String paymentId = accept(createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0)));
        report(paymentId);
        mockMvc.perform(get(PATH + paymentId).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.status").value("payer_reported"))
                .andExpect(jsonPath("$.proofUrl").value("https://example.com/proof.png"))
                .andExpect(jsonPath("$.payerReportedAt").exists())
                .andExpect(jsonPath("$.canConfirm").value(true))
                .andExpect(jsonPath("$.canReject").value(true))
                .andExpect(jsonPath("$.timeline", hasSize(2)));
    }

    @Test
    @DisplayName("Xac nhan da thu tien: thanh toan va booking deu da xac nhan; khong xac nhan lai duoc")
    void confirm() throws Exception {
        String bookingId = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        String paymentId = accept(bookingId);
        report(paymentId);

        confirm(paymentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andExpect(jsonPath("$.confirmedAt").exists())
                .andExpect(jsonPath("$.canConfirm").value(false))
                .andExpect(jsonPath("$.canReject").value(false))
                .andExpect(jsonPath("$.booking.status").value("confirmed"))
                .andExpect(jsonPath("$.timeline", hasSize(3)))
                .andExpect(jsonPath("$.timeline[2].newStatus").value("confirmed"));
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", bearer(playerToken)))
                .andExpect(jsonPath("$.status").value("confirmed"));
        confirm(paymentId).andExpect(status().isConflict());
        reject(paymentId, "Chua nhan duoc tien").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Tu choi kem ly do: nguoi choi thay ly do va bao lai duoc; ly do rong / qua ngan / qua dai bi chan")
    void reject() throws Exception {
        String bookingId = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        String paymentId = accept(bookingId);
        report(paymentId);

        reject(paymentId, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        reject(paymentId, "ab").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        reject(paymentId, "x".repeat(501)).andExpect(status().isBadRequest());
        mockMvc.perform(post(PATH + paymentId + "/reject").header("Authorization", bearer(ownerToken)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PATH + paymentId + "/reject").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH + paymentId).header("Authorization", bearer(ownerToken))).andExpect(jsonPath("$.status").value("payer_reported"));

        reject(paymentId, "  Chua thay tien trong tai khoan  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.rejectionReason").value("Chua thay tien trong tai khoan"))
                .andExpect(jsonPath("$.rejectedAt").exists())
                .andExpect(jsonPath("$.canConfirm").value(false))
                .andExpect(jsonPath("$.booking.status").value("awaiting_payment"))
                .andExpect(jsonPath("$.timeline[2].reason").value("Chua thay tien trong tai khoan"));
        mockMvc.perform(get("/api/v1/payments/" + paymentId).header("Authorization", bearer(playerToken)))
                .andExpect(jsonPath("$.rejectionReason").value("Chua thay tien trong tai khoan"));

        report(paymentId);
        mockMvc.perform(get(PATH + paymentId).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.status").value("payer_reported"))
                .andExpect(jsonPath("$.canConfirm").value(true))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    @Test
    @DisplayName("Da qua han: khong xac nhan duoc (409) nhung van tu choi duoc; chi tiet bao expired")
    void expiredReport() throws Exception {
        String paymentId = accept(createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0)));
        report(paymentId);
        Payment payment = paymentRepository.findById(UUID.fromString(paymentId)).orElseThrow();
        payment.setExpiredAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        paymentRepository.saveAndFlush(payment);

        mockMvc.perform(get(PATH + paymentId).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.expired").value(true))
                .andExpect(jsonPath("$.canConfirm").value(false))
                .andExpect(jsonPath("$.canReject").value(true));
        confirm(paymentId).andExpect(status().isConflict());
        reject(paymentId, "Qua han thanh toan").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Chu san khac 404; thanh toan khong ton tai 404; id sai 400; nguoi choi 403; chua dang nhap 401")
    void accessControl() throws Exception {
        String paymentId = accept(createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0)));
        report(paymentId);
        String otherToken = loginAs(persistOwner("other"));
        mockMvc.perform(get(PATH + paymentId).header("Authorization", bearer(otherToken))).andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + paymentId + "/confirm").header("Authorization", bearer(otherToken))).andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + paymentId + "/reject").header("Authorization", bearer(otherToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Khong dung\"}")).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + UUID.randomUUID()).header("Authorization", bearer(ownerToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "abc").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH + paymentId).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(post(PATH + paymentId + "/confirm").header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH + paymentId)).andExpect(status().isUnauthorized());
        // Chu san that van thay trang thai chua bi anh huong.
        mockMvc.perform(get(PATH + paymentId).header("Authorization", bearer(ownerToken))).andExpect(jsonPath("$.status").value("payer_reported"));
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String paymentId) throws Exception {
        return mockMvc.perform(post(PATH + paymentId + "/confirm").header("Authorization", bearer(ownerToken)));
    }

    private org.springframework.test.web.servlet.ResultActions reject(String paymentId, String reason) throws Exception {
        return mockMvc.perform(post(PATH + paymentId + "/reject").header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"));
    }

    private String accept(String bookingId) throws Exception {
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + bookingId + "/accept").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        String body = mockMvc.perform(get("/api/v1/payments/by-booking/" + bookingId).header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void report(String paymentId) throws Exception {
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/report-paid").header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"owner_bank_transfer\",\"proofUrl\":\"https://example.com/proof.png\"}"))
                .andExpect(status().isOk());
    }
}
