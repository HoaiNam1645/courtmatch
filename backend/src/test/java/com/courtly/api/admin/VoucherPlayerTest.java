package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.courtly.domain.voucher.Voucher;
import com.courtly.domain.voucher.VoucherRedemption;
import com.courtly.domain.voucher.VoucherRedemptionRepository;
import com.courtly.domain.voucher.VoucherRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import com.courtly.service.booking.BookingService;
import com.courtly.service.finance.CommissionService;
import com.courtly.service.voucher.VoucherEligibilityServiceAccess;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** B4 - nguoi choi xem, xem truoc va dung voucher khi dat san. */
class VoucherPlayerTest extends AdminApiTestBase {

    @Autowired private VoucherRepository voucherRepository;
    @Autowired private VoucherRedemptionRepository redemptionRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingCommissionRepository commissionRepository;
    @Autowired private PlayerStatisticsRepository statisticsRepository;
    @Autowired private CommissionService commissionService;
    @Autowired private BookingService bookingService;
    @Autowired private JdbcTemplate jdbc;

    private User owner;
    private String ownerToken;
    private Venue venue;
    private Court court;
    private User player;
    private String playerToken;
    private int dayOffset;

    @BeforeEach
    void setUp() throws Exception {
        ensureAdminSetup();
        owner = persistOwner("owner-voucher");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Voucher");
        court = venue.getCourts().getFirst();
        player = persistUser("player-voucher", Role.PLAYER);
        playerToken = loginAs(player);
    }

    private Voucher voucher(String code, String percent, String cap) {
        Voucher voucher = new Voucher();
        voucher.setId(UUID.randomUUID());
        voucher.setCode(code + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        voucher.setName("Voucher " + code);
        voucher.setDiscountPercent(new BigDecimal(percent));
        voucher.setMaxDiscountAmount(cap == null ? null : new BigDecimal(cap));
        voucher.setValidFrom(Instant.now().minus(1, ChronoUnit.DAYS));
        voucher.setValidUntil(Instant.now().plus(30, ChronoUnit.DAYS));
        voucher.setCreatedAt(Instant.now());
        return voucherRepository.saveAndFlush(voucher);
    }

    private String startTime() {
        return nextMonday().plusDays(dayOffset++).atTime(LocalTime.of(9, 0)).atZone(ZONE).toInstant().toString();
    }

    private org.springframework.test.web.servlet.ResultActions preview(String token, String code, String start) throws Exception {
        return mockMvc.perform(post("/api/v1/vouchers/preview").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"voucherCode":"%s","courtId":"%s","startTime":"%s","durationMinutes":90}
                        """.formatted(code, court.getId(), start)));
    }

    private org.springframework.test.web.servlet.ResultActions book(String token, String code) throws Exception {
        String body = code == null
                ? "{\"courtId\":\"%s\",\"startTime\":\"%s\",\"durationMinutes\":90,\"note\":null}".formatted(court.getId(), startTime())
                : "{\"courtId\":\"%s\",\"startTime\":\"%s\",\"durationMinutes\":90,\"note\":null,\"voucherCode\":\"%s\"}".formatted(court.getId(), startTime(), code);
        return mockMvc.perform(post("/api/v1/bookings").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String bookedId(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return objectMapper.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    // ------------------------------------------------------------ xem voucher

    @Test
    @DisplayName("Danh sach voucher cua nguoi choi: chi voucher dang phat hanh; da khoa, het han, chua bat dau khong hien; danh dau da dung va het luot")
    void listsIssuedVouchers() throws Exception {
        Voucher active = voucher("ACT", "10", null);
        Voucher locked = voucher("LCK", "10", null);
        locked.setStatus(Voucher.LOCKED);
        voucherRepository.saveAndFlush(locked);
        Voucher expired = voucher("EXP", "10", null);
        expired.setValidFrom(Instant.now().minus(10, ChronoUnit.DAYS));
        expired.setValidUntil(Instant.now().minus(1, ChronoUnit.DAYS));
        voucherRepository.saveAndFlush(expired);
        Voucher future = voucher("FUT", "10", null);
        future.setValidFrom(Instant.now().plus(1, ChronoUnit.DAYS));
        future.setValidUntil(Instant.now().plus(5, ChronoUnit.DAYS));
        voucherRepository.saveAndFlush(future);
        Voucher used = voucher("USD", "10", null);
        bookedId(book(playerToken, used.getCode()));

        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/v1/vouchers").header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        java.util.Map<String, JsonNode> byCode = new java.util.HashMap<>();
        list.forEach(item -> byCode.put(item.get("code").asText(), item));
        assertThat(byCode).containsKeys(active.getCode(), used.getCode());
        assertThat(byCode).doesNotContainKeys(locked.getCode(), expired.getCode(), future.getCode());
        assertThat(byCode.get(active.getCode()).get("status").asText()).isEqualTo("active");
        assertThat(byCode.get(active.getCode()).get("eligible").asBoolean()).isTrue();
        assertThat(byCode.get(used.getCode()).get("status").asText()).isEqualTo("used");
        mockMvc.perform(get("/api/v1/vouchers")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ xem truoc

    @Test
    @DisplayName("Xem truoc: giam theo phan tram, toi da bang tran giam, ma khong phan biet hoa thuong")
    void previewDiscounts() throws Exception {
        Voucher plain = voucher("PCT", "10", null);
        Voucher capped = voucher("CAP", "50", "30000");
        preview(playerToken, plain.getCode().toLowerCase(), startTime())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalAmount").value(180000))
                .andExpect(jsonPath("$.discountAmount").value(18000))
                .andExpect(jsonPath("$.payableAmount").value(162000))
                .andExpect(jsonPath("$.discountPercent").value(10.0));
        preview(playerToken, capped.getCode(), startTime())
                .andExpect(jsonPath("$.discountAmount").value(30000))
                .andExpect(jsonPath("$.payableAmount").value(150000));
        Voucher free = voucher("FREE", "100", null);
        preview(playerToken, free.getCode(), startTime()).andExpect(jsonPath("$.discountAmount").value(180000))
                .andExpect(jsonPath("$.payableAmount").value(0));
        assertThat(redemptionRepository.count()).isEqualTo(redemptionRepository.findAll().size());
    }

    @Test
    @DisplayName("Xem truoc bi tu choi: ma la, da khoa, het han, chua bat dau, het luot, da dung roi, thieu o")
    void previewRejections() throws Exception {
        preview(playerToken, "KHONG-CO", startTime()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_INVALID")).andExpect(jsonPath("$.fieldErrors.voucherCode").exists());
        Voucher locked = voucher("LOCK", "10", null);
        locked.setStatus(Voucher.LOCKED);
        voucherRepository.saveAndFlush(locked);
        preview(playerToken, locked.getCode(), startTime()).andExpect(status().isConflict());
        Voucher expired = voucher("OLD", "10", null);
        expired.setValidFrom(Instant.now().minus(9, ChronoUnit.DAYS));
        expired.setValidUntil(Instant.now().minus(1, ChronoUnit.DAYS));
        voucherRepository.saveAndFlush(expired);
        preview(playerToken, expired.getCode(), startTime()).andExpect(status().isConflict());
        Voucher future = voucher("SOON", "10", null);
        future.setValidFrom(Instant.now().plus(1, ChronoUnit.DAYS));
        future.setValidUntil(Instant.now().plus(3, ChronoUnit.DAYS));
        voucherRepository.saveAndFlush(future);
        preview(playerToken, future.getCode(), startTime()).andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/vouchers/preview").header("Authorization", bearer(playerToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"voucherCode\":\"\",\"courtId\":\"%s\",\"startTime\":\"%s\",\"durationMinutes\":90}".formatted(court.getId(), startTime())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/vouchers/preview").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------ dat san

    @Test
    @DisplayName("Dat san co voucher: gia goc giu nguyen, nguoi choi tra phan sau giam, thanh toan dung so tien sau giam")
    void bookingWithVoucher() throws Exception {
        Voucher voucher = voucher("BK", "20", null);
        String bookingId = bookedId(book(playerToken, voucher.getCode().toLowerCase()));
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", bearer(playerToken)))
                .andExpect(jsonPath("$.totalAmount").value(180000))
                .andExpect(jsonPath("$.discountAmount").value(36000))
                .andExpect(jsonPath("$.payableAmount").value(144000))
                .andExpect(jsonPath("$.voucherCode").value(voucher.getCode()));
        VoucherRedemption redemption = redemptionRepository.findByBookingId(UUID.fromString(bookingId)).orElseThrow();
        assertThat(redemption.getStatus()).isEqualTo("applied");
        assertThat(redemption.getOriginalAmount()).isEqualByComparingTo("180000");
        assertThat(redemption.getDiscountAmount()).isEqualByComparingTo("36000");

        mockMvc.perform(post("/api/v1/owner/booking-requests/" + bookingId + "/accept").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/payments/by-booking/" + bookingId).header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(144000));
    }

    @Test
    @DisplayName("Dat san khong voucher: khong giam, tra du gia goc; ma voucher sai chan dat san va khong tao booking")
    void bookingWithoutOrWithBadVoucher() throws Exception {
        String plain = bookedId(book(playerToken, null));
        mockMvc.perform(get("/api/v1/bookings/" + plain).header("Authorization", bearer(playerToken)))
                .andExpect(jsonPath("$.discountAmount").value(0)).andExpect(jsonPath("$.payableAmount").value(180000))
                .andExpect(jsonPath("$.voucherCode").doesNotExist());
        long before = bookingRepository.count();
        book(playerToken, "KHONG-CO-MA").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VOUCHER_INVALID"));
        assertThat(bookingRepository.count()).isEqualTo(before);
        book(playerToken, "   ").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Moi nguoi mot lan: dung roi khong dung lai; huy, tu choi hoac het han thi tra lai de dung lai")
    void oncePerPersonAndRelease() throws Exception {
        Voucher voucher = voucher("ONCE", "10", null);
        String first = bookedId(book(playerToken, voucher.getCode()));
        book(playerToken, voucher.getCode()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VOUCHER_INVALID"));

        // Huy boi nguoi choi
        mockMvc.perform(post("/api/v1/bookings/" + first + "/cancel").header("Authorization", bearer(playerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Doi lich\"}")).andExpect(status().isOk());
        assertThat(redemptionRepository.findByBookingId(UUID.fromString(first)).orElseThrow().getStatus()).isEqualTo("released");
        assertThat(redemptionRepository.findByBookingId(UUID.fromString(first)).orElseThrow().getReleasedAt()).isNotNull();

        // Dung lai, bi chu san tu choi
        String second = bookedId(book(playerToken, voucher.getCode()));
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + second + "/reject").header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"maintenance\",\"note\":\"Bao tri\"}")).andExpect(status().isOk());
        assertThat(redemptionRepository.findByBookingId(UUID.fromString(second)).orElseThrow().getStatus()).isEqualTo("released");

        // Dung lai, qua han chu san phan hoi
        String third = bookedId(book(playerToken, voucher.getCode()));
        Booking booking = bookingRepository.findById(UUID.fromString(third)).orElseThrow();
        booking.setOwnerResponseExpiresAt(Instant.now().minusSeconds(60));
        bookingRepository.saveAndFlush(booking);
        bookingService.expireOverdueBookings();
        assertThat(redemptionRepository.findByBookingId(UUID.fromString(third)).orElseThrow().getStatus()).isEqualTo("released");

        // Cuoi cung dung duoc va giu lai
        String fourth = bookedId(book(playerToken, voucher.getCode()));
        assertThat(redemptionRepository.findByBookingId(UUID.fromString(fourth)).orElseThrow().getStatus()).isEqualTo("applied");
        assertThat(redemptionRepository.countByVoucherIdAndStatus(voucher.getId(), "applied")).isEqualTo(1);
        // Huy lai lan nua khong loi (goi nhieu lan an toan).
        bookingService.expireOverdueBookings();
    }

    @Test
    @DisplayName("Gioi han tong luot: het luot thi nguoi khac khong dung duoc; luot tra lai duoc tinh lai")
    void totalLimit() throws Exception {
        Voucher voucher = voucher("LIM", "10", null);
        voucher.setTotalLimit(1);
        voucherRepository.saveAndFlush(voucher);
        String otherToken = loginAs(persistUser("player-khac", Role.PLAYER));
        String mine = bookedId(book(playerToken, voucher.getCode()));
        book(otherToken, voucher.getCode()).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Voucher da het luot su dung."));
        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/v1/vouchers").header("Authorization", bearer(otherToken)))
                .andReturn().getResponse().getContentAsString());
        for (JsonNode item : list) {
            if (voucher.getCode().equals(item.get("code").asText())) {
                assertThat(item.get("status").asText()).isEqualTo("exhausted");
                assertThat(item.get("remaining").asInt()).isZero();
            }
        }
        mockMvc.perform(post("/api/v1/bookings/" + mine + "/cancel").header("Authorization", bearer(playerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Doi lich\"}")).andExpect(status().isOk());
        book(otherToken, voucher.getCode()).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------ hoa hong

    @Test
    @DisplayName("Hoa hong tinh tren gia goc: phan giam Courtly chiu duoc tru vao cong no chu san; giam vuot hoa hong thi Courtly con no chu san")
    void commissionOffset() throws Exception {
        Voucher small = voucher("SM", "5", null);      // giam 9.000 < hoa hong 18.000
        Voucher big = voucher("BG", "20", null);       // giam 36.000 > hoa hong 18.000
        String smallBooking = bookedId(book(playerToken, small.getCode()));
        String bigBooking = bookedId(book(loginAs(persistUser("player-big", Role.PLAYER)), big.getCode()));
        String plainBooking = bookedId(book(loginAs(persistUser("player-plain", Role.PLAYER)), null));
        for (String id : new String[] {smallBooking, bigBooking, plainBooking}) {
            commissionService.createForCompletedBooking(bookingRepository.findById(UUID.fromString(id)).orElseThrow(), Instant.now());
        }
        BookingCommission smallC = commissionRepository.findByBookingId(UUID.fromString(smallBooking)).orElseThrow();
        assertThat(smallC.getGrossAmount()).isEqualByComparingTo("180000");
        assertThat(smallC.getCommissionAmount()).isEqualByComparingTo("9000");
        assertThat(smallC.getVoucherDiscount()).isEqualByComparingTo("9000");
        assertThat(smallC.getCourtlyOwed()).isEqualByComparingTo("0");
        BookingCommission bigC = commissionRepository.findByBookingId(UUID.fromString(bigBooking)).orElseThrow();
        assertThat(bigC.getCommissionAmount()).isEqualByComparingTo("0");
        assertThat(bigC.getVoucherDiscount()).isEqualByComparingTo("36000");
        assertThat(bigC.getCourtlyOwed()).isEqualByComparingTo("18000");
        BookingCommission plainC = commissionRepository.findByBookingId(UUID.fromString(plainBooking)).orElseThrow();
        assertThat(plainC.getCommissionAmount()).isEqualByComparingTo("18000");
        assertThat(plainC.getVoucherDiscount()).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------ dieu kien nhan

    @Test
    @DisplayName("Dieu kien Elo: chua du thi bi tu choi kem so can va so hien co; du thi dung duoc")
    void eloCondition() throws Exception {
        Voucher voucher = voucher("ELO", "10", null);
        voucher.setMinElo(new BigDecimal("1200"));
        voucherRepository.saveAndFlush(voucher);
        preview(playerToken, voucher.getCode(), startTime()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ban chua du dieu kien dung voucher nay."))
                .andExpect(jsonPath("$.fieldErrors.elo").exists());
        JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/v1/vouchers").header("Authorization", bearer(playerToken)))
                .andReturn().getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode item : list) {
            if (voucher.getCode().equals(item.get("code").asText())) {
                found = true;
                assertThat(item.get("eligible").asBoolean()).isFalse();
                assertThat(item.at("/unmet/0/code").asText()).isEqualTo("elo");
                assertThat(item.at("/unmet/0/required").decimalValue()).isEqualByComparingTo("1200");
            }
        }
        assertThat(found).isTrue();
        PlayerStatistics statistics = statisticsRepository.findById(player.getId()).orElseGet(() -> new PlayerStatistics(player));
        statistics.setRatingScore(new BigDecimal("1250"));
        statisticsRepository.saveAndFlush(statistics);
        preview(playerToken, voucher.getCode(), startTime()).andExpect(status().isOk());
    }

    private void booking_match(User user, String source, boolean confirmed, boolean excluded, LocalDate day) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO matches (id, match_type, scheduled_at, status, data_source, excluded_from_rating) VALUES (?, 'single', ?::timestamptz, 'completed', ?, ?)",
                id, day.atTime(10, 0).atZone(ZONE).toInstant().toString(), source, excluded);
        jdbc.update("INSERT INTO match_players (match_id, user_id, team_no, joined_status) VALUES (?, ?, 1, 'accepted')", id, user.getId());
        jdbc.update("INSERT INTO match_results (match_id, winning_team, confirmed_at) VALUES (?, 1, ?)", id,
                confirmed ? java.sql.Timestamp.from(Instant.now()) : null);
    }

    @Test
    @DisplayName("Dieu kien so tran va so tuan: chi tinh tran gan booking, da xac nhan, khong bi loai; tran thu cong khong tinh")
    void activityConditions() throws Exception {
        Voucher voucher = voucher("ACTV", "10", null);
        voucher.setMinBookingMatches(2);
        voucher.setMinActiveWeeks(2);
        voucherRepository.saveAndFlush(voucher);
        LocalDate today = LocalDate.now(ZONE);
        // Tran khong hop le: thu cong, chua xac nhan, bi loai khoi Elo
        booking_match(player, "manual", true, false, today);
        booking_match(player, "booking", false, false, today);
        booking_match(player, "booking", true, true, today);
        preview(playerToken, voucher.getCode(), startTime()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.booking_matches").exists()).andExpect(jsonPath("$.fieldErrors.active_weeks").exists());

        booking_match(player, "booking", true, false, today);
        preview(playerToken, voucher.getCode(), startTime()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.booking_matches").exists()).andExpect(jsonPath("$.fieldErrors.active_weeks").exists());
        // Mot tran hop le o tuan truoc: du 2 tran va 2 tuan lien tiep
        booking_match(player, "booking", true, false, today.minusWeeks(1));
        preview(playerToken, voucher.getCode(), startTime()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Chuoi tuan: tinh lui tu tuan nay hoac tuan truoc, dut tuan thi dem lai tu dau")
    void streakCalculation() {
        LocalDate today = LocalDate.of(2026, 11, 18); // thu Tu
        LocalDate thisWeek = VoucherEligibilityServiceAccess.weekStart(today);
        assertThat(VoucherEligibilityServiceAccess.streak(java.util.Set.of(), today)).isZero();
        assertThat(VoucherEligibilityServiceAccess.streak(java.util.Set.of(thisWeek), today)).isEqualTo(1);
        assertThat(VoucherEligibilityServiceAccess.streak(java.util.Set.of(thisWeek.minusWeeks(1)), today)).isEqualTo(1);
        assertThat(VoucherEligibilityServiceAccess.streak(java.util.Set.of(thisWeek, thisWeek.minusWeeks(1), thisWeek.minusWeeks(2)), today)).isEqualTo(3);
        assertThat(VoucherEligibilityServiceAccess.streak(java.util.Set.of(thisWeek, thisWeek.minusWeeks(2)), today)).isEqualTo(1);
        assertThat(VoucherEligibilityServiceAccess.streak(java.util.Set.of(thisWeek.minusWeeks(2)), today)).isZero();
        assertThat(VoucherEligibilityServiceAccess.weekStart(LocalDate.of(2026, 11, 22))).isEqualTo(LocalDate.of(2026, 11, 16));
    }
}
