package com.courtly.api.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.CourtStatus;
import com.courtly.common.enums.PaymentStatus;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.common.enums.WebhookProcessStatus;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.payment.SepayWebhookLog;
import com.courtly.domain.payment.SepayWebhookLogRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtPriceRule;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueOperatingHours;
import com.courtly.domain.venue.VenueRepository;
import com.courtly.service.payment.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiem thu thanh toan SePay: 2.1.33 chon phuong thuc, 2.1.34 tao giao dich,
 * 2.1.35/36 QR va thong tin chuyen khoan, 2.1.37 webhook, 2.1.38 doi chieu,
 * 2.1.39 trang thai, 2.1.40 xac nhan, 2.1.41 that bai, 2.1.42 het han.
 */
@SpringBootTest(properties = "courtly.payment.sepay.api-key=khoa-kiem-thu")
@AutoConfigureMockMvc
@Transactional
class PaymentControllerTest {

    private static final String BOOKINGS = "/api/v1/bookings";
    private static final String PAYMENTS = "/api/v1/payments";
    private static final String WEBHOOK = "/api/v1/payments/sepay/webhook";
    private static final String API_KEY = "Apikey khoa-kiem-thu";
    private static final String PASSWORD = "Courtly@123";
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private SepayWebhookLogRepository webhookLogRepository;
    @Autowired private PaymentService paymentService;
    @Autowired private PasswordEncoder passwordEncoder;

    private String token;
    private Court court;
    private LocalDate futureDate;

    @BeforeEach
    void seed() throws Exception {
        if (roleRepository.findByCode(Role.PLAYER).isEmpty()) {
            Role role = new Role(Role.PLAYER, "Nguoi choi", null);
            role.setId(UUID.randomUUID());
            roleRepository.save(role);
        }

        futureDate = LocalDate.now(ZONE).plusDays(1);
        while (futureDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            futureDate = futureDate.plusDays(1);
        }

        User owner = persistUser("owner");
        token = loginAs(persistUser("player"));

        Venue venue = new Venue();
        venue.setId(UUID.randomUUID());
        venue.setOwner(owner);
        venue.setName("San Kiem Thu Thanh Toan");
        venue.setSlug("kt-tt-" + UUID.randomUUID());
        venue.setPhone("0900000000");
        venue.setAddress("34 Kiem Thu");
        venue.setStatus(VenueStatus.ACTIVE);
        venue.setApprovalStatus(ApprovalStatus.APPROVED);
        venue.setLatitude(BigDecimal.valueOf(16.05));
        venue.setLongitude(BigDecimal.valueOf(108.20));

        for (int day = 1; day <= 7; day++) {
            VenueOperatingHours hours = new VenueOperatingHours();
            hours.setId(UUID.randomUUID());
            hours.setVenue(venue);
            hours.setDayOfWeek((short) day);
            hours.setOpenTime(LocalTime.of(8, 0));
            hours.setCloseTime(LocalTime.of(22, 0));
            venue.getOperatingHours().add(hours);
        }

        court = new Court();
        court.setId(UUID.randomUUID());
        court.setVenue(venue);
        court.setName("San A");
        court.setCourtCode("A");
        court.setStatus(CourtStatus.ACTIVE);

        CourtPriceRule rule = new CourtPriceRule();
        rule.setId(UUID.randomUUID());
        rule.setCourt(court);
        rule.setStartTime(LocalTime.of(8, 0));
        rule.setEndTime(LocalTime.of(22, 0));
        rule.setPricePerHour(BigDecimal.valueOf(120_000));
        rule.setStatus(ActiveStatus.ACTIVE);
        rule.setEffectiveFrom(LocalDate.now(ZONE).minusYears(1));
        court.getPriceRules().add(rule);
        venue.getCourts().add(court);

        venueRepository.saveAndFlush(venue);
    }

    // --- 2.1.33, 2.1.34 Tao giao dich cung luc voi don -----------------------

    @Test
    @DisplayName("Tao don thi sinh luon giao dich SePay dang cho thanh toan")
    void createBookingAlsoCreatesPayment() throws Exception {
        String bookingId = createBooking(LocalTime.of(9, 0));

        Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(
                UUID.fromString(bookingId)).orElseThrow();

        assertThat(payment.getProvider()).isEqualTo("sepay");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(180_000));
        assertThat(payment.getExpiredAt()).isNotNull();
    }

    // --- 2.1.35, 2.1.36, 2.1.39 Man hinh thanh toan --------------------------

    @Test
    @DisplayName("Chi tiet giao dich tra du QR, ngan hang, noi dung chuyen khoan va dem nguoc")
    void paymentDetailHasEverythingTheScreenNeeds() throws Exception {
        String bookingId = createBooking(LocalTime.of(10, 0));
        Payment payment = paymentOf(bookingId);

        mockMvc.perform(get(PAYMENTS + "/" + payment.getId()).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.amount").value(180000))
                .andExpect(jsonPath("$.qrUrl").value(org.hamcrest.Matchers.containsString("qr.sepay.vn")))
                .andExpect(jsonPath("$.bankName").isNotEmpty())
                .andExpect(jsonPath("$.bankAccountNo").isNotEmpty())
                .andExpect(jsonPath("$.bankAccountName").isNotEmpty())
                .andExpect(jsonPath("$.transferContent").isNotEmpty())
                .andExpect(jsonPath("$.secondsRemaining").isNumber());
    }

    @Test
    @DisplayName("Noi dung chuyen khoan chi gom chu va so de ngan hang khong cat mat")
    void transferContentIsAlphanumericOnly() throws Exception {
        String bookingId = createBooking(LocalTime.of(11, 0));
        assertThat(paymentOf(bookingId).getTransferContent()).matches("[A-Za-z0-9]+");
    }

    @Test
    @DisplayName("Nguoi khac khong xem duoc giao dich, tra 404 chu khong phai 403")
    void otherUserCannotReadPayment() throws Exception {
        String bookingId = createBooking(LocalTime.of(12, 0));
        Payment payment = paymentOf(bookingId);
        String otherToken = loginAs(persistUser("khac"));

        mockMvc.perform(get(PAYMENTS + "/" + payment.getId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    // --- 2.1.37 Webhook ------------------------------------------------------

    @Test
    @DisplayName("Webhook khong co khoa thi bi tu choi")
    void webhookWithoutKeyIsRejected() throws Exception {
        mockMvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("TXN-1", "CT0001", 180_000, "in")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Webhook sai khoa thi bi tu choi")
    void webhookWithWrongKeyIsRejected() throws Exception {
        mockMvc.perform(post(WEBHOOK).header("Authorization", "Apikey sai-khoa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(webhookBody("TXN-2", "CT0001", 180_000, "in")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Webhook gui lai cung mot giao dich khong duoc xu ly hai lan")
    void duplicateWebhookIsProcessedOnce() throws Exception {
        String bookingId = createBooking(LocalTime.of(13, 0));
        String content = paymentOf(bookingId).getTransferContent();

        sendWebhook("TXN-TRUNG", content, 180_000, "in").andExpect(status().isOk());
        sendWebhook("TXN-TRUNG", content, 180_000, "in")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Da xu ly truoc do"));

        assertThat(webhookLogRepository.findAll().stream()
                .filter(l -> "TXN-TRUNG".equals(l.getTransactionCode())).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Giao dich tien ra bi bo qua, khong lam don thanh da thanh toan")
    void outgoingTransferIsIgnored() throws Exception {
        String bookingId = createBooking(LocalTime.of(14, 0));
        String content = paymentOf(bookingId).getTransferContent();

        sendWebhook("TXN-RA", content, 180_000, "out").andExpect(status().isOk());

        assertThat(paymentOf(bookingId).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(logFor("TXN-RA").getProcessedStatus()).isEqualTo(WebhookProcessStatus.IGNORED);
    }

    // --- 2.1.38 Doi chieu + 2.1.40 Xac nhan don ------------------------------

    @Test
    @DisplayName("Webhook dung noi dung va du tien thi don chuyen sang da xac nhan")
    void matchingWebhookConfirmsBooking() throws Exception {
        String bookingId = createBooking(LocalTime.of(15, 0));
        String content = paymentOf(bookingId).getTransferContent();

        sendWebhook("TXN-OK", content, 180_000, "in")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        Payment payment = paymentOf(bookingId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getProviderTransactionId()).isEqualTo("TXN-OK");

        Booking booking = bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getExpiresAt()).isNull();
        assertThat(logFor("TXN-OK").getProcessedStatus()).isEqualTo(WebhookProcessStatus.PROCESSED);
    }

    @Test
    @DisplayName("Ngan hang chen them chu vao noi dung thi van doi chieu dung")
    void matchesWhenBankAppendsExtraWords() throws Exception {
        String bookingId = createBooking(LocalTime.of(16, 0));
        String content = paymentOf(bookingId).getTransferContent();

        sendWebhook("TXN-THEM-CHU", "CHUYEN TIEN " + content + " GD 123", 180_000, "in")
                .andExpect(status().isOk());

        assertThat(paymentOf(bookingId).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("Chuyen thieu tien thi khong xac nhan don, ghi nhat ky that bai")
    void underpaymentDoesNotConfirm() throws Exception {
        String bookingId = createBooking(LocalTime.of(17, 0));
        String content = paymentOf(bookingId).getTransferContent();

        sendWebhook("TXN-THIEU", content, 100_000, "in").andExpect(status().isOk());

        assertThat(paymentOf(bookingId).getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(bookingRepository.findById(UUID.fromString(bookingId)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(logFor("TXN-THIEU").getProcessedStatus()).isEqualTo(WebhookProcessStatus.FAILED);
    }

    @Test
    @DisplayName("Noi dung khong khop don nao thi ghi nhat ky de doi soat, khong gay loi")
    void unmatchedWebhookIsLogged() throws Exception {
        sendWebhook("TXN-LAC", "NAP TIEN TAY", 500_000, "in")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        SepayWebhookLog logEntry = logFor("TXN-LAC");
        assertThat(logEntry.getProcessedStatus()).isEqualTo(WebhookProcessStatus.IGNORED);
        assertThat(logEntry.getPayment()).isNull();
    }

    // --- 2.1.41 That bai, thu lai; 2.1.42 Het han ----------------------------

    @Test
    @DisplayName("Giao dich qua han tu chuyen sang het han")
    void overduePaymentExpires() throws Exception {
        String bookingId = createBooking(LocalTime.of(18, 0));
        Payment payment = paymentOf(bookingId);
        payment.setExpiredAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        paymentRepository.saveAndFlush(payment);

        // Khong khang dinh con so tuyet doi: database dung chung co the con giao dich qua han
        // tu du lieu mau. Chi quan tam giao dich cua chinh test nay.
        assertThat(paymentService.expireOverduePayments()).isPositive();
        assertThat(paymentOf(bookingId).getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    @Test
    @DisplayName("Thu lai mo lai cua so thanh toan va giu nguyen noi dung chuyen khoan")
    void retryReopensPaymentWindow() throws Exception {
        String bookingId = createBooking(LocalTime.of(19, 0));
        Payment payment = paymentOf(bookingId);
        String contentBefore = payment.getTransferContent();
        payment.setStatus(PaymentStatus.EXPIRED);
        paymentRepository.saveAndFlush(payment);

        mockMvc.perform(post(PAYMENTS + "/" + payment.getId() + "/retry")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.transferContent").value(contentBefore))
                .andExpect(jsonPath("$.secondsRemaining").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("Khong thu lai duoc giao dich dang cho thanh toan")
    void cannotRetryPendingPayment() throws Exception {
        String bookingId = createBooking(LocalTime.of(20, 0));

        mockMvc.perform(post(PAYMENTS + "/" + paymentOf(bookingId).getId() + "/retry")
                        .header("Authorization", bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_RETRYABLE"));
    }

    // --- Tien ich ------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions sendWebhook(
            String txnId, String content, long amount, String type) throws Exception {
        return mockMvc.perform(post(WEBHOOK).header("Authorization", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody(txnId, content, amount, type)));
    }

    private String webhookBody(String txnId, String content, long amount, String type) {
        return """
                {"id":"%s","gateway":"MBBank","transactionDate":"2026-10-02 10:00:00",
                 "accountNumber":"0359000111","transferType":"%s","transferAmount":%d,
                 "content":"%s","referenceCode":"REF-%s"}
                """.formatted(txnId, type, amount, content, txnId);
    }

    private Payment paymentOf(String bookingId) {
        return paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(
                UUID.fromString(bookingId)).orElseThrow();
    }

    private SepayWebhookLog logFor(String transactionCode) {
        return webhookLogRepository.findAll().stream()
                .filter(l -> transactionCode.equals(l.getTransactionCode()))
                .findFirst().orElseThrow();
    }

    private String createBooking(LocalTime start) throws Exception {
        String body = """
                {"courtId":"%s","startTime":"%s","durationMinutes":90,"note":null}
                """.formatted(court.getId(), at(start));
        String response = mockMvc.perform(post(BOOKINGS).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String at(LocalTime time) {
        return futureDate.atTime(time).atZone(ZONE).toInstant().toString();
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private User persistUser(String prefix) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nguoi Dung " + prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.getUserRoles().add(new UserRole(user, roleRepository.findByCode(Role.PLAYER).orElseThrow()));
        user.setPlayerProfile(new PlayerProfile(user));
        return userRepository.saveAndFlush(user);
    }

    private String loginAs(User user) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailOrPhone":"%s","password":"%s"}
                                """.formatted(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
